package com.proj.autodeploy.deployment.dto;

import com.proj.autodeploy.deployment.domain.Deployment;
import java.time.Instant;

/**
 * 배포 단건 상세. (05_api_spec.md §4.3)
 * 생성(§4.1)·취소(§4.4) 응답에도 재사용한다(아직 안 채워진 필드는 null → non_null 직렬화로 생략).
 */
public record DeploymentDetailResponse(
        Long deploymentId,
        Long projectId,
        Long previousDeploymentId,
        String branch,
        String commitHash,
        String commitMessage,
        String imageRepository,
        String imageTag,
        String status,
        String triggerType,
        String failureReason,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {

    public static DeploymentDetailResponse from(Deployment d) {
        return new DeploymentDetailResponse(
                d.getId(),
                d.getProjectId(),
                d.getPreviousDeploymentId(),
                d.getBranch(),
                d.getCommitHash(),
                d.getCommitMessage(),
                d.getImageRepository(),
                d.getImageTag(),
                d.getStatus().name(),
                d.getTriggerType().name(),
                d.getFailureReason(),
                d.getStartedAt(),
                d.getFinishedAt(),
                d.getCreatedAt()
        );
    }
}
