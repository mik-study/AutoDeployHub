package com.proj.autodeploy.deployment.sse;

/**
 * SSE {@code status} 이벤트 payload. (05_api_spec.md §4.7)
 */
public record DeploymentStatusEvent(String status) {
}
