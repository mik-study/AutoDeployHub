package com.proj.autodeploy.project.dto;

import com.proj.autodeploy.project.domain.Project;
import java.time.Instant;

/**
 * 프로젝트 단건 상세. webhookSecret 은 마스킹된다. (05_api_spec.md §2.3)
 */
public record ProjectResponse(
        Long projectId,
        String name,
        String description,
        String repositoryUrl,
        String defaultBranch,
        String rootDirectory,
        String buildType,
        String healthCheckPath,
        Integer healthCheckPort,
        Integer healthCheckTimeoutSeconds,
        Integer healthCheckIntervalSeconds,
        String subdomain,
        String webhookSecret,
        String status,
        Instant createdAt
) {

    private static final String MASKED_SECRET = "whs_****";

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getRepositoryUrl(),
                p.getDefaultBranch(),
                p.getRootDirectory(),
                p.getBuildType().name(),
                p.getHealthCheckPath(),
                p.getHealthCheckPort(),
                p.getHealthCheckTimeoutSeconds(),
                p.getHealthCheckIntervalSeconds(),
                p.getSubdomain(),
                MASKED_SECRET,
                p.getStatus().name(),
                p.getCreatedAt()
        );
    }
}
