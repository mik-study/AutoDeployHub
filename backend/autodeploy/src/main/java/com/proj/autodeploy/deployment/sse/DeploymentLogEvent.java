package com.proj.autodeploy.deployment.sse;

import com.proj.autodeploy.deployment.domain.DeploymentLog;

/**
 * SSE {@code log} 이벤트 payload. (05_api_spec.md §4.7)
 *
 * <p>{@code createdAt} 을 넣지 않는 건 명세가 스트림 이벤트에서는 그걸 요구하지 않기 때문이다
 * (스냅샷 §4.6 에는 있다). 실시간 스트림에서는 클라이언트가 수신 시각을 그대로 쓰면 된다.
 */
public record DeploymentLogEvent(
        Long sequence,
        String level,
        String message
) {

    public static DeploymentLogEvent from(DeploymentLog log) {
        return new DeploymentLogEvent(log.getSequence(), log.getLevel().name(), log.getMessage());
    }
}
