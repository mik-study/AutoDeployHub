package com.proj.autodeploy.environment;

import com.proj.autodeploy.environment.dto.CreateEnvRequest;
import com.proj.autodeploy.environment.dto.EnvListItemResponse;
import com.proj.autodeploy.environment.dto.EnvResponse;
import com.proj.autodeploy.environment.dto.UpdateEnvRequest;
import com.proj.autodeploy.global.response.ApiResponse;
import com.proj.autodeploy.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EnvironmentVariable API. (05_api_spec.md §3)
 */
@RestController
@RequestMapping("/api/projects/{projectId}/env")
@RequiredArgsConstructor
public class EnvironmentVariableController {

    private final EnvironmentVariableService environmentVariableService;

    /** §3.1 등록 → 201. */
    @PostMapping
    public ResponseEntity<ApiResponse<EnvResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateEnvRequest request) {
        EnvResponse response = environmentVariableService.create(principal.userId(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /** §3.2 목록 → 200 (secret 은 `****`). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EnvListItemResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.of(
                environmentVariableService.list(principal.userId(), projectId)));
    }

    /** §3.3 값 수정 → 200. */
    @PatchMapping("/{envId}")
    public ResponseEntity<ApiResponse<EnvResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @PathVariable Long envId,
            @Valid @RequestBody UpdateEnvRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                environmentVariableService.update(principal.userId(), projectId, envId, request)));
    }

    /** §3.4 삭제 → 204. */
    @DeleteMapping("/{envId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @PathVariable Long envId) {
        environmentVariableService.delete(principal.userId(), projectId, envId);
        return ResponseEntity.noContent().build();
    }
}
