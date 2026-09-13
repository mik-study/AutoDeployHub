package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.lock.DeploymentLockManager;
import com.proj.autodeploy.project.ProjectRepository;
import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.runtime.RuntimeInstanceRepository;
import com.proj.autodeploy.runtime.domain.RuntimeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커(컨슈머) 경로의 배포 상태 전이 전용 서비스.
 *
 * <p>{@link DeploymentService} 는 "사용자 요청" 경로라 userId 로 소유권을 검증하지만, 워커 경로에는
 * 사용자가 없다. 대신 모든 메서드가 <b>짧은 트랜잭션</b>이어야 한다 — 실제 파이프라인(docker build 등)은
 * 이 트랜잭션 바깥에서 돈다.
 *
 * <p>과제 3·4에서 단계별 상태 전이와 {@code DeploymentLog} 적재 메서드가 여기에 추가된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentLifecycleService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLockManager lockManager;
    private final ProjectRepository projectRepository;
    private final RuntimeInstanceRepository runtimeInstanceRepository;

    /**
     * 컨슈머 진입점. QUEUED → CLONING 전이 + 시작 시각 기록.
     *
     * <p>멱등성 가드를 겸한다. 같은 메시지를 두 번 받아도(브로커 재전송 등) 두 번째는 QUEUED 가
     * 아니므로 false 를 반환하고 아무것도 하지 않는다.
     *
     * @return 실제로 전이했으면 true, 이미 처리됐거나 대상이 없으면 false
     */
    @Transactional
    public boolean startProcessing(Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null) {
            log.warn("deployment not found. deploymentId={}", deploymentId);
            return false;
        }
        if (deployment.getStatus() != DeploymentStatus.QUEUED) {
            log.info("skip: status is {} (not QUEUED). deploymentId={}",
                    deployment.getStatus(), deploymentId);
            return false;
        }
        // findById 로 가져온 엔티티는 영속 상태라 save() 없이도 커밋 시 dirty checking 으로 UPDATE 된다.
        deployment.transitionTo(DeploymentStatus.CLONING);
        deployment.markStarted();
        return true;
    }

    /**
     * 파이프라인 실패 처리. 전이가 불가능한 상태(이미 종착 등)면 로그만 남기고 넘어간다.
     */
    @Transactional
    public void markFailed(Long deploymentId, String reason) {
        deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
            if (!deployment.getStatus().canTransitionTo(DeploymentStatus.FAILED)) {
                log.warn("cannot mark FAILED from {}. deploymentId={}",
                        deployment.getStatus(), deploymentId);
                return;
            }
            deployment.transitionTo(DeploymentStatus.FAILED);
            deployment.markFailureReason(reason);
            releaseLock(deployment);
        });
    }

    /**
     * 파이프라인 성공 처리. {@code SWITCHING_TRAFFIC → SUCCEEDED} 전이 + 락 해제.
     *
     * <p>여기까지 오려면 상태머신상 CLONING→…→SWITCHING_TRAFFIC 을 전부 거쳐야 한다.
     */
    @Transactional
    public void markSucceeded(Long deploymentId) {
        deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
            if (!deployment.getStatus().canTransitionTo(DeploymentStatus.SUCCEEDED)) {
                log.warn("cannot mark SUCCEEDED from {}. deploymentId={}",
                        deployment.getStatus(), deploymentId);
                return;
            }
            deployment.transitionTo(DeploymentStatus.SUCCEEDED);
            releaseLock(deployment);
        });
    }

    /**
     * 파이프라인 중간 단계 전이. (과제 3 Worker 가 단계마다 호출)
     *
     * @throws IllegalStateException 상태머신이 허용하지 않는 전이일 때 - 단계 누락을 조기에 드러낸다
     */
    @Transactional
    public void transitionTo(Long deploymentId, DeploymentStatus next) {
        Deployment deployment = deploymentRepository.findById(deploymentId).orElseThrow(
                () -> new IllegalStateException("deployment not found: " + deploymentId));
        deployment.transitionTo(next);
    }

    /**
     * Worker 가 파이프라인 내내 참조할 값 스냅샷. (과제 3)
     *
     * <p>엔티티 대신 값 객체를 만들어 넘기는 이유는 파이프라인이 트랜잭션 밖에서 돌기 때문이다.
     * detached 엔티티를 들고 다니면 지연 로딩 접근에서 터진다.
     */
    @Transactional(readOnly = true)
    public DeploymentWorker.DeploymentContext loadContext(Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId).orElseThrow(
                () -> new IllegalStateException("deployment not found: " + deploymentId));
        Project project = projectRepository.findById(deployment.getProjectId()).orElseThrow(
                () -> new IllegalStateException("project not found: " + deployment.getProjectId()));
        return new DeploymentWorker.DeploymentContext(
                deploymentId, project, deployment.getBranch(), deployment.getCommitHash());
    }

    /** 빌드된 이미지 좌표 기록. (과제 3) */
    @Transactional
    public void assignImage(Long deploymentId, String imageRepository, String imageTag) {
        deploymentRepository.findById(deploymentId)
                .ifPresent(d -> d.assignImage(imageRepository, imageTag));
    }

    /** clone 후 확인한 실제 커밋 기록. (수동 배포는 commitHash 없이 시작한다) */
    @Transactional
    public void resolveCommit(Long deploymentId, String commitHash) {
        deploymentRepository.findById(deploymentId).ifPresent(d -> d.resolveCommit(commitHash));
    }

    /** health 통과 → 이 인스턴스를 active 로. (4주차는 BLUE 단일이라 곧 전체 트래픽) */
    @Transactional
    public void activateRuntimeInstance(Long runtimeInstanceId) {
        runtimeInstanceRepository.findById(runtimeInstanceId).ifPresent(instance -> {
            instance.activate();
            instance.changeStatus(RuntimeStatus.RUNNING);
        });
    }

    /** 이전 컨테이너 정리 시 상태 기록. */
    @Transactional
    public void changeRuntimeStatus(Long runtimeInstanceId, RuntimeStatus status) {
        runtimeInstanceRepository.findById(runtimeInstanceId).ifPresent(instance -> {
            instance.deactivate();
            instance.changeStatus(status);
        });
    }

    /** 파이프라인이 실패하면 그 배포가 띄운 인스턴스도 FAILED 로 표시한다. */
    @Transactional
    public void failRuntimeInstanceOf(Long deploymentId) {
        runtimeInstanceRepository.findByDeploymentId(deploymentId).ifPresent(instance -> {
            instance.deactivate();
            instance.changeStatus(RuntimeStatus.FAILED);
        });
    }

    /**
     * 종착 도달 시 프로젝트 배포 락 해제. (과제 5)
     *
     * <p>안 풀면 TTL(기본 30분)이 만료될 때까지 그 프로젝트는 배포가 막힌다.
     * 락 값이 deploymentId 라서 자기 락일 때만 지워진다.
     */
    private void releaseLock(Deployment deployment) {
        lockManager.unlock(deployment.getProjectId(), deployment.getId());
    }
}
