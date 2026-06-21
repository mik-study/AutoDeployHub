package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.DeploymentTriggerType;
import com.proj.autodeploy.deployment.dto.CreateDeploymentRequest;
import com.proj.autodeploy.deployment.dto.DeploymentDetailResponse;
import com.proj.autodeploy.deployment.dto.DeploymentSummaryResponse;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.ProjectService;
import com.proj.autodeploy.project.domain.Project;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    /** 중복 배포 가드용 "진행 중" 상태 집합 (DeploymentStatus.isInProgress() 기준, 한 곳에서 산출). */
    private static final List<DeploymentStatus> IN_PROGRESS_STATUSES =
            Arrays.stream(DeploymentStatus.values())
                    .filter(DeploymentStatus::isInProgress)
                    .toList();

    private final DeploymentRepository deploymentRepository;
    private final ProjectService projectService;
    private final DeploymentPublisher deploymentPublisher;

    /**
     * 배포 요청. (05_api_spec.md §4.1) PENDING → QUEUED 후 발행 seam 호출.
     */
    @Transactional
    public DeploymentDetailResponse create(Long userId, Long projectId, CreateDeploymentRequest request) {
        Project project = projectService.getOwnedProject(userId, projectId);

        // 중복 배포 가드. TODO(과제 5): Redis 분산락으로 교체
        if (deploymentRepository.existsByProjectIdAndStatusIn(projectId, IN_PROGRESS_STATUSES)) {
            throw new ApiException(ErrorCode.DEPLOYMENT_ALREADY_IN_PROGRESS);
        }

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
        deployment = deploymentRepository.save(deployment);

        // TODO(과제 2): 트랜잭션 커밋 후 발행(@TransactionalEventListener)으로 다듬기. 현재는 로그 스텁.
        deploymentPublisher.publish(deployment);

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
