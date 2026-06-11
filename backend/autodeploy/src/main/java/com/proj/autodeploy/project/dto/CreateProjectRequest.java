package com.proj.autodeploy.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotBlank @Pattern(regexp = "^https://github\\.com/.+", message = "GitHub repository URL 형식이어야 합니다.")
        String repositoryUrl,
        String defaultBranch,
        String rootDirectory,
        String healthCheckPath,
        Integer healthCheckPort,
        Integer healthCheckTimeoutSeconds,
        Integer healthCheckIntervalSeconds,
        @Pattern(regexp = "^$|^[a-z0-9-]{1,63}$", message = "subdomain 은 소문자/숫자/하이픈만 허용됩니다.")
        String subdomain
) {
}
