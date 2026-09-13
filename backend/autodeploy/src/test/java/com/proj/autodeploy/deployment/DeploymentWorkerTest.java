package com.proj.autodeploy.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.project.ProjectRepository;
import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.runtime.RuntimeInstanceRepository;
import com.proj.autodeploy.runtime.domain.RuntimeColor;
import com.proj.autodeploy.runtime.domain.RuntimeStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Worker 파이프라인 통합 테스트. (과제 3)
 *
 * <p>{@code autodeploy.pipeline.mode=mock} (테스트 properties) 이므로 {@code FakeCommandRunner} 와
 * {@code AlwaysHealthyChecker} 가 등록된다 — git/docker 없이 <b>상태머신 전 경로와 로그 적재</b>를 검증한다.
 *
 * <p>{@code @Transactional} 을 붙이지 않았다. 파이프라인이 단계마다 별도의 짧은 트랜잭션을 열기
 * 때문에, 테스트 트랜잭션으로 감싸면 실제 커밋 동작과 달라진다. 대신 프로젝트/배포를 매 테스트마다
 * 새로 만들어 서로 간섭하지 않게 한다.
 */
@SpringBootTest
class DeploymentWorkerTest {

    @Autowired DeploymentWorker worker;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentLogRepository deploymentLogRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired RuntimeInstanceRepository runtimeInstanceRepository;

    @Test
    @DisplayName("★ CLONING → … → SUCCEEDED 전 경로를 상태머신 위반 없이 통과한다")
    void runsFullPipelineToSuccess() {
        Long deploymentId = givenCloningDeployment();

        worker.run(deploymentId);

        Deployment finished = deploymentRepository.findById(deploymentId).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(DeploymentStatus.SUCCEEDED);
        assertThat(finished.getFinishedAt()).isNotNull();
        // 단계를 건너뛰었다면 transitionTo 가 IllegalStateException 을 던져 여기 못 온다.
    }

    @Test
    @DisplayName("빌드된 이미지 좌표가 배포에 기록된다")
    void recordsImageCoordinates() {
        Long deploymentId = givenCloningDeployment();

        worker.run(deploymentId);

        Deployment finished = deploymentRepository.findById(deploymentId).orElseThrow();
        assertThat(finished.getImageRepository()).isEqualTo("autodeploy-runtime");
        assertThat(finished.getImageTag()).endsWith("-deploy-" + deploymentId);
    }

    @Test
    @DisplayName("BLUE RuntimeInstance 가 생성되고 health 통과 후 active 가 된다")
    void createsAndActivatesBlueInstance() {
        Long deploymentId = givenCloningDeployment();

        worker.run(deploymentId);

        var instance = runtimeInstanceRepository.findByDeploymentId(deploymentId).orElseThrow();
        assertThat(instance.getColor()).isEqualTo(RuntimeColor.BLUE);
        assertThat(instance.getStatus()).isEqualTo(RuntimeStatus.RUNNING);
        assertThat(instance.isActive()).isTrue();
        assertThat(instance.getContainerName()).contains("-d" + deploymentId + "-blue");
    }

    @Test
    @DisplayName("각 단계가 DeploymentLog 로 남고 sequence 가 1부터 연속이다")
    void appendsContiguousLogs() {
        Long deploymentId = givenCloningDeployment();

        worker.run(deploymentId);

        List<Long> sequences = deploymentLogRepository
                .findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(deploymentId, 0L)
                .stream().map(l -> l.getSequence()).toList();

        assertThat(sequences).isNotEmpty();
        assertThat(sequences.getFirst()).isEqualTo(1L);
        // 연속성: [1..n]
        assertThat(sequences).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, sequences.size()).boxed().toList());
    }

    @Test
    @DisplayName("파이프라인 로그에 환경변수 주입과 트래픽 전환 단계가 보인다")
    void logsCoverEveryStage() {
        Long deploymentId = givenCloningDeployment();

        worker.run(deploymentId);

        String joined = String.join("\n", deploymentLogRepository
                .findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(deploymentId, 0L)
                .stream().map(l -> l.getMessage()).toList());

        assertThat(joined)
                .contains("저장소 clone 시작")
                .contains("Dockerfile 검증 통과")
                .contains("이미지 빌드 완료")
                .contains("push 를 건너뜁니다")
                .contains("환경변수")
                .contains("health check 통과")
                .contains("트래픽 전환 완료")
                .contains("배포 성공");
    }

    // ------------------------------------------------------------------

    private Long givenCloningDeployment() {
        return saveCloningDeployment(projectRepository, deploymentRepository);
    }

    private static Long saveCloningDeployment(ProjectRepository projects, DeploymentRepository deployments) {
        Project project = projects.save(Project.builder()
                .ownerId(1L)
                .name("worker-demo")
                .repositoryUrl("https://github.com/myorg/demo")
                .defaultBranch("main")
                .subdomain("worker-" + System.nanoTime())
                .webhookSecret("secret")
                // 폴링이 오래 걸리지 않도록 짧게
                .healthCheckTimeoutSeconds(5)
                .healthCheckIntervalSeconds(1)
                .build());

        Deployment deployment = Deployment.builder()
                .projectId(project.getId())
                .branch("main")
                .build();
        deployment.transitionTo(DeploymentStatus.QUEUED);
        deployment.transitionTo(DeploymentStatus.CLONING);   // 컨슈머가 해주는 전이
        deployment.markStarted();
        return deployments.save(deployment).getId();
    }
}
