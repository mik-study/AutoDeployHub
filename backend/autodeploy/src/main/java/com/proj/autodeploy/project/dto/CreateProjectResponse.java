package com.proj.autodeploy.project.dto;

import com.proj.autodeploy.project.domain.Project;
import java.time.Instant;

/**
 * 프로젝트 생성 응답. webhookSecret 은 생성 시 1회만 평문 노출된다. (05_api_spec.md §2.1)
 */
public record CreateProjectResponse(
        Long projectId,
        String name,
        String subdomain,
        String repositoryUrl,
        String defaultBranch,
        String status,
        String webhookUrl,
        String webhookSecret,
        Instant createdAt
) {

    public static CreateProjectResponse from(Project p, String webhookUrl, String plainSecret) {
        return new CreateProjectResponse(
                p.getId(),
                p.getName(),
                p.getSubdomain(),
                p.getRepositoryUrl(),
                p.getDefaultBranch(),
                p.getStatus().name(),
                webhookUrl,
                plainSecret,
                p.getCreatedAt()
        );
    }
}
