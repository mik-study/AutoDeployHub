package com.proj.autodeploy.deployment.dto;

import com.proj.autodeploy.deployment.domain.Deployment;
import java.time.Instant;

/**
 * 배포 이력 목록 행. (05_api_spec.md §4.2)
 */
public record DeploymentSummaryResponse(
        Long deploymentId,
        String branch,
        String commitHash,
        String commitMessage,
        String status,
        String triggerType,
        Instant startedAt,
        Instant finishedAt
) {

    public static DeploymentSummaryResponse from(Deployment d) {
        return new DeploymentSummaryResponse(
                d.getId(),
                d.getBranch(),
                d.getCommitHash(),
                d.getCommitMessage(),
                d.getStatus().name(),
                d.getTriggerType().name(),
                d.getStartedAt(),
                d.getFinishedAt()
        );
    }
}
