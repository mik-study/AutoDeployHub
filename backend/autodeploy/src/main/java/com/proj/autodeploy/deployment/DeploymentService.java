package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.DeploymentTriggerType;
import com.proj.autodeploy.deployment.dto.CreateDeploymentRequest;
import com.proj.autodeploy.deployment.dto.DeploymentDetailResponse;
import com.proj.autodeploy.deployment.dto.DeploymentSummaryResponse;
import com.proj.autodeploy.deployment.lock.DeploymentLockManager;
import com.proj.autodeploy.deployment.messaging.DeploymentRequested;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.ProjectService;
import com.proj.autodeploy.project.domain.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;
    private final DeploymentLockManager lockManager;

    /**
     * 배포 요청. (05_api_spec.md §4.1) PENDING → QUEUED 후 발행 이벤트 등록.
     */
    @Transactional
    public DeploymentDetailResponse create(Long userId, Long projectId, CreateDeploymentRequest request) {
        Project project = projectService.getOwnedProject(userId, projectId);

        String branch = (request.branch() != null && !request.branch().isBlank())
                ? request.branch().trim()
                : project.getDefaultBranch();

        Deployment deployment = Deployment.builder()
                .projectId(projectId)
                .branch(branch)
                .commitHash(request.commitHash())
                .triggerType(DeploymentTriggerType.MANUAL)
                .build();                              // status = PENDING
        deployment.transitionTo(DeploymentStatus.QUEUED);
        deployment = deploymentRepository.save(deployment);   // IDENTITY 라 여기서 id 가 채워진다

        // 중복 배포 가드 (과제 5): 프로젝트 단위 분산락. 획득 실패 = 이미 진행 중인 배포가 있다.
        // 락 값이 deploymentId 라서 저장 이후에 잡는다.
        if (!lockManager.tryLock(projectId, deployment.getId())) {
            // 거절된 요청이 이력에 남지 않도록 직접 지운다. 롤백에만 맡기면, 호출부가 이미 트랜잭션
            // 안일 때(내부 @Transactional 이 참여만 하는 경우) row 가 그대로 남는다.
            deploymentRepository.delete(deployment);
            deploymentRepository.flush();
            throw new ApiException(ErrorCode.DEPLOYMENT_ALREADY_IN_PROGRESS);
        }

        // 큐 발행은 커밋 이후에 일어난다. (DeploymentEventListener 가 AFTER_COMMIT 에서 수신)
        // 커밋 전에 보내면 컨슈머가 아직 없는 row 를 조회해 배포가 증발할 수 있다.
        eventPublisher.publishEvent(new DeploymentRequested(
                deployment.getId(),
                projectId,
                branch,
                deployment.getCommitHash()));

        return DeploymentDetailResponse.from(deployment);
    }

    /** 배포 이력. (05_api_spec.md §4.2) */
    @Transactional(readOnly = true)
    public Page<DeploymentSummaryResponse> list(Long userId, Long projectId, Pageable pageable) {
        projectService.getOwnedProject(userId, projectId); // 소유권 검증
        return deploymentRepository.findByProjectId(projectId, pageable)
                .map(DeploymentSummaryResponse::from);
    }

    /** 배포 단건 조회. (05_api_spec.md §4.3) */
    @Transactional(readOnly = true)
    public DeploymentDetailResponse get(Long userId, Long deploymentId) {
        return DeploymentDetailResponse.from(getOwnedDeployment(userId, deploymentId));
    }

    /** 배포 취소. (05_api_spec.md §4.4) PENDING/QUEUED 에서만 가능. */
    @Transactional
    public DeploymentDetailResponse cancel(Long userId, Long deploymentId) {
        Deployment deployment = getOwnedDeployment(userId, deploymentId);
        DeploymentStatus status = deployment.getStatus();
        // 명세 에러코드(422)를 정확히 던지기 위해 transitionTo 예외에 의존하지 않고 먼저 명시적으로 체크.
        if (status != DeploymentStatus.PENDING && status != DeploymentStatus.QUEUED) {
            throw new ApiException(ErrorCode.DEPLOYMENT_NOT_CANCELABLE);
        }
        deployment.transitionTo(DeploymentStatus.CANCELED);
        // 종착 도달 → 락 해제. 안 풀면 TTL(기본 30분) 동안 그 프로젝트가 배포 불가로 묶인다.
        lockManager.unlock(deployment.getProjectId(), deployment.getId());
        return DeploymentDetailResponse.from(deployment);
    }

    /** deployment 로드 + (소속 프로젝트 기준) 소유권 검증. */
    private Deployment getOwnedDeployment(Long userId, Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.DEPLOYMENT_NOT_FOUND, "deploymentId", deploymentId));
        projectService.getOwnedProject(userId, deployment.getProjectId());
        return deployment;
    }
}
