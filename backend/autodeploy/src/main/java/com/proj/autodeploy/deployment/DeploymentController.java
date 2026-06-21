package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.dto.CreateDeploymentRequest;
import com.proj.autodeploy.deployment.dto.DeploymentDetailResponse;
import com.proj.autodeploy.deployment.dto.DeploymentSummaryResponse;
import com.proj.autodeploy.global.response.ApiResponse;
import com.proj.autodeploy.global.response.PagedResponse;
import com.proj.autodeploy.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deployment API. (05_api_spec.md §4.1~4.4)
 * 경로가 /api/projects/{id}/deployments 와 /api/deployments/{id} 두 갈래라 메서드별 전체 경로를 둔다.
 */
@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    /** §4.1 배포 요청 → 202 (QUEUED). body 는 선택(없으면 defaultBranch). */
    @PostMapping("/api/projects/{projectId}/deployments")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @RequestBody(required = false) CreateDeploymentRequest request) {
        CreateDeploymentRequest req = (request != null) ? request : new CreateDeploymentRequest(null, null);
        DeploymentDetailResponse response = deploymentService.create(principal.userId(), projectId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** §4.2 배포 이력 (최신순). */
    @GetMapping("/api/projects/{projectId}/deployments")
    public ResponseEntity<PagedResponse<DeploymentSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DeploymentSummaryResponse> page = deploymentService.list(principal.userId(), projectId, pageable);
        return ResponseEntity.ok(PagedResponse.of(page));
    }

    /** §4.3 배포 단건 조회. */
    @GetMapping("/api/deployments/{deploymentId}")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId) {
        return ResponseEntity.ok(ApiResponse.of(deploymentService.get(principal.userId(), deploymentId)));
    }

    /** §4.4 배포 취소 (PENDING/QUEUED 만). */
    @PostMapping("/api/deployments/{deploymentId}/cancel")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId) {
        return ResponseEntity.ok(ApiResponse.of(deploymentService.cancel(principal.userId(), deploymentId)));
    }
}
