package com.proj.autodeploy.project.dto;

import com.proj.autodeploy.project.domain.Project;

/**
 * 프로젝트 목록 항목. (05_api_spec.md §2.2)
 * lastDeployment 는 Deployment 도메인 연동(4주차) 전까지 null.
 */
public record ProjectSummaryResponse(
        Long projectId,
        String name,
        String subdomain,
        String status,
        Object lastDeployment
) {

    public static ProjectSummaryResponse from(Project p) {
        return new ProjectSummaryResponse(
                p.getId(),
                p.getName(),
                p.getSubdomain(),
                p.getStatus().name(),
                null
        );
    }
}
