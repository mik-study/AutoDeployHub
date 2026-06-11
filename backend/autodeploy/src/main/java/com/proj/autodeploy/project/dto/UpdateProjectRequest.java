package com.proj.autodeploy.project.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH: null 이 아닌 필드만 수정한다.
 * repositoryUrl, subdomain 은 MVP 에서 변경 불가이므로 필드에 포함하지 않는다. (05_api_spec.md §2.4)
 */
public record UpdateProjectRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        String defaultBranch,
        String rootDirectory,
        String healthCheckPath,
        Integer healthCheckPort,
        Integer healthCheckTimeoutSeconds,
        Integer healthCheckIntervalSeconds
) {
}
