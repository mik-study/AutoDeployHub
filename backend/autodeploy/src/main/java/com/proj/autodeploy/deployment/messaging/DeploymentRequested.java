package com.proj.autodeploy.deployment.messaging;

/**
 * deploy.queue 메시지 계약. (공통 모임 합의 스키마)
 *
 * <p>용도가 둘이다.
 * <ul>
 *   <li>Spring 애플리케이션 이벤트 payload — {@code DeploymentService} 가 커밋 전에 발행</li>
 *   <li>RabbitMQ 메시지 본문 — AFTER_COMMIT 시점에 JSON 으로 직렬화되어 큐로</li>
 * </ul>
 *
 * <p><b>엔티티(Deployment)를 그대로 실어보내지 않는다.</b> 이유가 셋이다.
 * <ol>
 *   <li>엔티티는 커밋 후 detached 상태라 지연 로딩 필드 접근이 위험하다</li>
 *   <li>엔티티 필드가 바뀔 때마다 큐 메시지 포맷이 말없이 바뀐다 (계약이 깨진다)</li>
 *   <li>컨슈머가 필요로 하는 건 id 몇 개뿐인데 불필요하게 큰 페이로드가 된다</li>
 * </ol>
 */
public record DeploymentRequested(
        Long deploymentId,
        Long projectId,
        String branch,
        String commitHash
) {
}
