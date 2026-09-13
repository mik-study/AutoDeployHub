package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.dto.LogSnapshotResponse;
import com.proj.autodeploy.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 배포 로그 API. (05_api_spec.md §4.6~4.7, 과제 4)
 */
@RestController
@RequestMapping("/api/deployments/{deploymentId}/logs")
@RequiredArgsConstructor
public class DeploymentLogController {

    private final DeploymentLogService deploymentLogService;

    /** §4.6 스냅샷 조회. */
    @GetMapping
    public ResponseEntity<LogSnapshotResponse> snapshot(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId,
            @RequestParam(defaultValue = "0") Long fromSequence,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(
                deploymentLogService.snapshot(principal.userId(), deploymentId, fromSequence, limit));
    }

    /**
     * §4.7 실시간 스트림.
     *
     * <p>브라우저 {@code EventSource} 는 커스텀 헤더를 못 붙이므로, 이 엔드포인트만
     * {@code ?access_token=} 쿼리 파라미터 인증을 허용한다
     * ({@code JwtAuthenticationFilter} 참고).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return deploymentLogService.stream(principal.userId(), deploymentId, lastEventId);
    }
}
