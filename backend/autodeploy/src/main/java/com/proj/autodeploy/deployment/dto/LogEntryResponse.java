package com.proj.autodeploy.deployment.dto;

import com.proj.autodeploy.deployment.domain.DeploymentLog;
import java.time.Instant;

/**
 * 배포 로그 한 줄. (05_api_spec.md §4.6)
 */
public record LogEntryResponse(
        Long sequence,
        String level,
        String message,
        Instant createdAt
) {

    public static LogEntryResponse from(DeploymentLog log) {
        return new LogEntryResponse(
                log.getSequence(),
                log.getLevel().name(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
