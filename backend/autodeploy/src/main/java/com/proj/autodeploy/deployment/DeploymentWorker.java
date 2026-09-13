package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.LogLevel;
import com.proj.autodeploy.deployment.pipeline.CommandRunner;
import com.proj.autodeploy.deployment.pipeline.HealthChecker;
import com.proj.autodeploy.environment.EnvironmentVariableService;
import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.runtime.RuntimeInstanceRepository;
import com.proj.autodeploy.runtime.domain.RuntimeColor;
import com.proj.autodeploy.runtime.domain.RuntimeInstance;
import com.proj.autodeploy.runtime.domain.RuntimeStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 배포 파이프라인 본체. (과제 3)
 *
 * <pre>
 * git clone → Dockerfile 검사 → docker build → (push no-op) → docker run → health check
 *          → 트래픽 전환 → SUCCEEDED
 * </pre>
 *
 * <p>호출 규약 (과제 2에서 확정):
 * <ul>
 *   <li>호출 시점에 deployment 는 이미 {@code CLONING} 으로 전이돼 있다</li>
 *   <li><b>트랜잭션 밖</b>에서 호출된다 — 단계별 상태 저장만 짧은 트랜잭션으로 끊는다
 *       ({@link DeploymentLifecycleService}). docker build 가 몇 분씩 걸리는데 트랜잭션을
 *       붙들고 있으면 커넥션 풀이 마른다</li>
 *   <li>예외를 던져도 된다 — 호출부({@code DeployQueueListener})가 FAILED 처리한다</li>
 * </ul>
 *
 * <p><b>상태 건너뛰기 금지.</b> {@code DeploymentStatus} 는 단계 생략을 허용하지 않아서
 * ({@code transitionTo} 가 {@code IllegalStateException}) 레지스트리가 없는 4주차에도
 * {@code PUSHING_IMAGE} 를 no-op 으로 통과시켜야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentWorker {

    private static final String IMAGE_REPOSITORY = "autodeploy-runtime";
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration DOCKER_CMD_TIMEOUT = Duration.ofMinutes(2);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLifecycleService lifecycleService;
    private final DeploymentLogService logService;
    private final EnvironmentVariableService environmentVariableService;
    private final RuntimeInstanceRepository runtimeInstanceRepository;
    private final CommandRunner commandRunner;
    private final HealthChecker healthChecker;

    /** 배포 서브도메인 베이스. traefik 라우팅 규칙과 맞춰야 한다. */
    @Value("${autodeploy.deploy.base-domain:autodeploy.test}")
    private String baseDomain;

    /** 배포된 컨테이너가 붙을 도커 네트워크. traefik 과 같은 네트워크여야 라우팅된다. */
    @Value("${autodeploy.deploy.network:autodeploy-network}")
    private String network;

    public void run(Long deploymentId) {
        DeploymentContext ctx = loadContext(deploymentId);
        Path workspace = null;
        try {
            workspace = createWorkspace(deploymentId);

            clone(ctx, workspace);
            Path buildContext = resolveBuildContext(ctx, workspace);

            checkDockerfile(ctx, buildContext);
            buildImage(ctx, buildContext);
            pushImage(ctx);
            RuntimeInstance instance = runContainer(ctx);
            healthCheck(ctx, instance);
            switchTraffic(ctx, instance);
            complete(ctx);

        } catch (RuntimeException e) {
            // 상태 정리(FAILED)는 호출부가 한다. 여기서는 실패 사유를 로그 스트림에 남기고 전파한다.
            logService.append(deploymentId, LogLevel.ERROR, "배포 실패: " + rootMessage(e));
            markInstanceFailed(deploymentId);
            throw e;
        } finally {
            deleteQuietly(workspace);
        }
    }

    /** 임시 워크스페이스. checked IOException 을 여기서 unchecked 로 바꿔 파이프라인 흐름을 단순하게 유지한다. */
    private Path createWorkspace(Long deploymentId) {
        try {
            return Files.createTempDirectory("autodeploy-" + deploymentId + "-");
        } catch (IOException e) {
            throw new IllegalStateException("작업 디렉터리를 만들 수 없습니다.", e);
        }
    }

    // ------------------------------------------------------------------
    // 단계별 구현
    // ------------------------------------------------------------------

    /** CLONING 상태를 유지한 채 실제 clone. (진입 시 이미 CLONING) */
    private void clone(DeploymentContext ctx, Path workspace) {
        logService.info(ctx.deploymentId(), "저장소 clone 시작: %s (branch=%s)"
                .formatted(ctx.repositoryUrl(), ctx.branch()));

        // --depth 1: 배포에는 최신 커밋만 있으면 되고, 전체 히스토리를 받으면 큰 저장소에서 몇 분씩 걸린다.
        int exit = exec(ctx, workspace.getParent(), List.of(
                "git", "clone", "--depth", "1", "--branch", ctx.branch(),
                ctx.repositoryUrl(), workspace.toString()), CLONE_TIMEOUT);
        requireSuccess(exit, "git clone");

        resolveCommitHash(ctx, workspace);
        logService.info(ctx.deploymentId(), "clone 완료");
    }

    /** CLONING → CHECKING_DOCKERFILE. */
    private void checkDockerfile(DeploymentContext ctx, Path buildContext) {
        transition(ctx, DeploymentStatus.CHECKING_DOCKERFILE);
        logService.info(ctx.deploymentId(), "Dockerfile 확인: " + buildContext.resolve("Dockerfile"));

        Path dockerfile = buildContext.resolve("Dockerfile");
        if (!Files.isRegularFile(dockerfile)) {
            throw new IllegalStateException(
                    "Dockerfile 을 찾을 수 없습니다: rootDirectory=" + ctx.rootDirectory());
        }
        try {
            if (Files.size(dockerfile) == 0) {
                throw new IllegalStateException("Dockerfile 이 비어 있습니다.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Dockerfile 을 읽을 수 없습니다.", e);
        }
        logService.info(ctx.deploymentId(), "Dockerfile 검증 통과");
    }

    /** CHECKING_DOCKERFILE → BUILDING. */
    private void buildImage(DeploymentContext ctx, Path buildContext) {
        transition(ctx, DeploymentStatus.BUILDING);
        String imageTag = "project-%d-deploy-%d".formatted(ctx.projectId(), ctx.deploymentId());
        String fullImage = IMAGE_REPOSITORY + ":" + imageTag;

        logService.info(ctx.deploymentId(), "이미지 빌드 시작: " + fullImage);
        int exit = exec(ctx, buildContext, List.of(
                "docker", "build", "-t", fullImage, "."), BUILD_TIMEOUT);
        requireSuccess(exit, "docker build");

        saveImageCoordinates(ctx.deploymentId(), imageTag);
        ctx.image = fullImage;
        logService.info(ctx.deploymentId(), "이미지 빌드 완료: " + fullImage);
    }

    /**
     * BUILDING → PUSHING_IMAGE. <b>4주차에는 no-op.</b>
     *
     * <p>로컬 단일 호스트라 레지스트리가 없다. 그래도 상태를 통과시켜야 하는 건 상태머신이
     * {@code BUILDING → DEPLOYING} 직행을 허용하지 않기 때문이다.
     */
    private void pushImage(DeploymentContext ctx) {
        transition(ctx, DeploymentStatus.PUSHING_IMAGE);
        logService.info(ctx.deploymentId(),
                "레지스트리가 없어 push 를 건너뜁니다 (로컬 이미지 사용). 상태만 통과합니다.");
    }

    /** PUSHING_IMAGE → DEPLOYING. env 주입 + traefik label 로 컨테이너 기동. */
    private RuntimeInstance runContainer(DeploymentContext ctx) {
        transition(ctx, DeploymentStatus.DEPLOYING);

        String containerName = containerName(ctx);
        removePreviousContainers(ctx);

        // 과제 6 Env API 의 복호화 값. secret 이 섞여 있으므로 값은 절대 로그에 남기지 않는다.
        Map<String, String> env = environmentVariableService.resolveForRuntime(ctx.projectId());
        logService.info(ctx.deploymentId(), "환경변수 %d 건 주입".formatted(env.size()));

        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "-d",
                "--name", containerName,
                "--network", network,
                "--restart", "unless-stopped"));
        env.forEach((key, value) -> {
            command.add("-e");
            // 셸을 거치지 않으므로 따옴표로 감쌀 필요가 없다. 값에 공백이 있어도 그대로 전달된다.
            command.add(key + "=" + value);
        });
        command.addAll(traefikLabels(ctx, containerName));
        command.add(ctx.image);

        logService.info(ctx.deploymentId(), "컨테이너 기동: " + containerName);
        // 명령 자체에 secret 값이 들어 있어 출력을 로그 스트림에 흘리지 않는다.
        int exit = commandRunner.run(null, command, DOCKER_CMD_TIMEOUT, line -> { });
        requireSuccess(exit, "docker run");

        RuntimeInstance instance = runtimeInstanceRepository.save(RuntimeInstance.builder()
                .projectId(ctx.projectId())
                .deploymentId(ctx.deploymentId())
                .containerName(containerName)
                .imageTag(ctx.image)
                .color(RuntimeColor.BLUE)          // 1차는 BLUE 단일. Blue-Green 전환은 5주차
                .port(RuntimeColor.BLUE.defaultPort())
                .active(false)
                .status(RuntimeStatus.STARTING)
                .build());
        logService.info(ctx.deploymentId(), "컨테이너 기동 완료 (color=BLUE)");
        return instance;
    }

    /** DEPLOYING → HEALTH_CHECKING. project 설정값으로 폴링. */
    private void healthCheck(DeploymentContext ctx, RuntimeInstance instance) {
        transition(ctx, DeploymentStatus.HEALTH_CHECKING);

        // traefik 이 아니라 컨테이너에 직접 붙는다. 라우팅 설정 문제와 앱 기동 실패를 구분하기 위해서다.
        String url = "http://%s:%d%s".formatted(
                instance.getContainerName(), ctx.healthCheckPort(), ctx.healthCheckPath());
        Duration timeout = Duration.ofSeconds(ctx.healthCheckTimeoutSeconds());
        Duration interval = Duration.ofSeconds(ctx.healthCheckIntervalSeconds());

        logService.info(ctx.deploymentId(), "health check 시작: %s (timeout=%ds, interval=%ds)"
                .formatted(url, timeout.toSeconds(), interval.toSeconds()));

        long deadline = System.nanoTime() + timeout.toNanos();
        int attempt = 0;
        while (System.nanoTime() < deadline) {
            attempt++;
            if (healthChecker.isHealthy(url)) {
                logService.info(ctx.deploymentId(), "health check 통과 (%d번째 시도)".formatted(attempt));
                return;
            }
            sleep(interval);
        }
        throw new IllegalStateException(
                "health check 실패: %ds 안에 %s 가 응답하지 않았습니다.".formatted(timeout.toSeconds(), url));
    }

    /**
     * HEALTH_CHECKING → SWITCHING_TRAFFIC.
     *
     * <p>4주차에는 <b>trivial</b> — 띄운 BLUE 하나가 곧 active 다. traefik label 을 이미 붙여둬서
     * 컨테이너가 뜨는 순간 라우팅은 이미 살아 있다. 실제 무중단 전환(GREEN 기동 후 스위치)은 5주차.
     */
    private void switchTraffic(DeploymentContext ctx, RuntimeInstance instance) {
        transition(ctx, DeploymentStatus.SWITCHING_TRAFFIC);

        activateInstance(instance.getId());
        logService.info(ctx.deploymentId(), "트래픽 전환 완료: https://project-%d.%s"
                .formatted(ctx.projectId(), baseDomain));
    }

    /** SWITCHING_TRAFFIC → SUCCEEDED. 종착이므로 락도 여기서 풀린다. */
    private void complete(DeploymentContext ctx) {
        lifecycleService.markSucceeded(ctx.deploymentId());
        logService.info(ctx.deploymentId(), "배포 성공");
        logService.publishStatus(ctx.deploymentId(), DeploymentStatus.SUCCEEDED);
    }

    // ------------------------------------------------------------------
    // 보조
    // ------------------------------------------------------------------

    private void transition(DeploymentContext ctx, DeploymentStatus next) {
        lifecycleService.transitionTo(ctx.deploymentId(), next);
        logService.publishStatus(ctx.deploymentId(), next);
    }

    private int exec(DeploymentContext ctx, Path workingDirectory, List<String> command, Duration timeout) {
        return commandRunner.run(workingDirectory, command, timeout,
                line -> logService.info(ctx.deploymentId(), line));
    }

    private void requireSuccess(int exitCode, String what) {
        if (exitCode != 0) {
            throw new IllegalStateException("%s 실패 (exit=%d)".formatted(what, exitCode));
        }
    }

    /** {@code rootDirectory} 를 적용한 빌드 컨텍스트. 워크스페이스 밖으로 벗어나지 못하게 막는다. */
    private Path resolveBuildContext(DeploymentContext ctx, Path workspace) {
        String root = ctx.rootDirectory();
        if (root == null || root.isBlank() || "/".equals(root)) {
            return workspace;
        }
        Path resolved = workspace.resolve(root.replaceFirst("^/+", "")).normalize();
        // "../../etc" 같은 값으로 워크스페이스 밖 파일을 빌드 컨텍스트로 삼는 걸 차단한다.
        if (!resolved.startsWith(workspace)) {
            throw new IllegalStateException("rootDirectory 가 저장소 밖을 가리킵니다: " + root);
        }
        return resolved;
    }

    /** 수동 배포는 commitHash 없이 들어오므로 clone 후 실제 커밋을 기록한다. */
    private void resolveCommitHash(DeploymentContext ctx, Path workspace) {
        if (ctx.commitHash() != null && !ctx.commitHash().isBlank()) {
            return;
        }
        StringBuilder out = new StringBuilder();
        try {
            commandRunner.run(workspace, List.of("git", "rev-parse", "HEAD"),
                    DOCKER_CMD_TIMEOUT, line -> out.append(line.trim()));
        } catch (RuntimeException e) {
            log.debug("could not resolve commit hash for deployment {}", ctx.deploymentId(), e);
            return;
        }
        String hash = out.toString();
        if (!hash.isBlank()) {
            saveCommitHash(ctx.deploymentId(), hash.substring(0, Math.min(40, hash.length())));
        }
    }

    private List<String> traefikLabels(DeploymentContext ctx, String containerName) {
        // 라우터/서비스 이름은 컨테이너마다 달라야 한다. 같은 이름을 재사용하면 이전 배포의
        // 라우팅 규칙과 충돌한다.
        String router = "app-" + ctx.projectId();
        return List.of(
                "-l", "traefik.enable=true",
                "-l", "traefik.http.routers.%s.rule=Host(`project-%d.%s`)"
                        .formatted(router, ctx.projectId(), baseDomain),
                "-l", "traefik.http.routers.%s.entrypoints=web".formatted(router),
                "-l", "traefik.http.services.%s.loadbalancer.server.port=%d"
                        .formatted(router, ctx.healthCheckPort()),
                "-l", "autodeploy.project.id=" + ctx.projectId(),
                "-l", "autodeploy.deployment.id=" + ctx.deploymentId());
    }

    private String containerName(DeploymentContext ctx) {
        // deploymentId 를 넣어 매번 고유하게 만든다 - runtime_instances.container_name 이 unique 다.
        return "autodeploy-p%d-d%d-blue".formatted(ctx.projectId(), ctx.deploymentId());
    }

    /**
     * 같은 프로젝트의 이전 컨테이너 정리.
     *
     * <p>4주차는 BLUE 단일 컨테이너라 새로 띄우기 전에 이전 것을 내려야 한다. 5주차 Blue-Green 에서는
     * 이 자리가 "GREEN 기동 → 전환 → 구 BLUE 정리" 로 바뀐다.
     */
    private void removePreviousContainers(DeploymentContext ctx) {
        List<RuntimeInstance> previous = runtimeInstanceRepository.findByProjectId(ctx.projectId()).stream()
                .filter(i -> i.getStatus() != RuntimeStatus.STOPPED)
                .sorted(Comparator.comparing(RuntimeInstance::getId))
                .toList();
        for (RuntimeInstance instance : previous) {
            // 이미 없는 컨테이너를 지우려 해도 실패로 보지 않는다 - 정리가 목적이다.
            commandRunner.run(null, List.of("docker", "rm", "-f", instance.getContainerName()),
                    DOCKER_CMD_TIMEOUT, line -> { });
            stopInstance(instance.getId());
            logService.info(ctx.deploymentId(), "이전 컨테이너 정리: " + instance.getContainerName());
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("health check 대기 중 중단되었습니다.", e);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private void deleteQuietly(Path workspace) {
        if (workspace == null) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 정리 실패는 배포 결과에 영향을 주지 않는다.
                }
            });
        } catch (IOException e) {
            log.warn("failed to clean workspace {}", workspace, e);
        }
    }

    // --- 짧은 트랜잭션들 (파이프라인 자체는 트랜잭션 밖에서 돈다) ---

    private void saveImageCoordinates(Long deploymentId, String imageTag) {
        lifecycleService.assignImage(deploymentId, IMAGE_REPOSITORY, imageTag);
    }

    private void saveCommitHash(Long deploymentId, String commitHash) {
        lifecycleService.resolveCommit(deploymentId, commitHash);
    }

    private void activateInstance(Long instanceId) {
        lifecycleService.activateRuntimeInstance(instanceId);
    }

    private void stopInstance(Long instanceId) {
        lifecycleService.changeRuntimeStatus(instanceId, RuntimeStatus.STOPPED);
    }

    private void markInstanceFailed(Long deploymentId) {
        lifecycleService.failRuntimeInstanceOf(deploymentId);
    }

    private DeploymentContext loadContext(Long deploymentId) {
        return lifecycleService.loadContext(deploymentId);
    }

    /**
     * 파이프라인이 필요로 하는 값 스냅샷.
     *
     * <p>엔티티를 들고 다니지 않는 이유는 트랜잭션 밖에서 detached 엔티티를 만지는 게 위험하기
     * 때문이다. 값만 복사해두고, 쓰기는 매번 짧은 트랜잭션으로 한다.
     */
    public static final class DeploymentContext {

        private final Long deploymentId;
        private final Long projectId;
        private final String branch;
        private final String commitHash;
        private final String repositoryUrl;
        private final String rootDirectory;
        private final String healthCheckPath;
        private final int healthCheckPort;
        private final int healthCheckTimeoutSeconds;
        private final int healthCheckIntervalSeconds;

        /** 빌드 후 채워지는 유일한 가변 필드. */
        private String image;

        public DeploymentContext(Long deploymentId, Project project, String branch, String commitHash) {
            this.deploymentId = deploymentId;
            this.projectId = project.getId();
            this.branch = branch;
            this.commitHash = commitHash;
            this.repositoryUrl = project.getRepositoryUrl();
            this.rootDirectory = project.getRootDirectory();
            this.healthCheckPath = project.getHealthCheckPath();
            this.healthCheckPort = project.getHealthCheckPort();
            this.healthCheckTimeoutSeconds = project.getHealthCheckTimeoutSeconds();
            this.healthCheckIntervalSeconds = project.getHealthCheckIntervalSeconds();
        }

        Long deploymentId() {
            return deploymentId;
        }

        Long projectId() {
            return projectId;
        }

        String branch() {
            return branch;
        }

        String commitHash() {
            return commitHash;
        }

        String repositoryUrl() {
            return repositoryUrl;
        }

        String rootDirectory() {
            return rootDirectory;
        }

        String healthCheckPath() {
            return healthCheckPath;
        }

        int healthCheckPort() {
            return healthCheckPort;
        }

        int healthCheckTimeoutSeconds() {
            return healthCheckTimeoutSeconds;
        }

        int healthCheckIntervalSeconds() {
            return healthCheckIntervalSeconds;
        }
    }
}
