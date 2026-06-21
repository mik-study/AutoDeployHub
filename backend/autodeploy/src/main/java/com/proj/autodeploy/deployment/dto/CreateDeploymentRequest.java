package com.proj.autodeploy.deployment.dto;

/**
 * 배포 요청. (05_api_spec.md §4.1)
 * branch 미지정 시 프로젝트의 defaultBranch 사용, commitHash 미지정 시 해당 branch HEAD(과제 3 Worker에서 결정).
 * 둘 다 nullable.
 */
public record CreateDeploymentRequest(
        String branch,
        String commitHash
) {
}
