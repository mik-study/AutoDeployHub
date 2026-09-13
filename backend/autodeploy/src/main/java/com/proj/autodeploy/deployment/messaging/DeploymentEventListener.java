package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import com.proj.autodeploy.deployment.lock.DeploymentLockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "언제 발행할 것인가"를 담당. (어떻게 발행하는지는 {@link RabbitDeploymentPublisher} 의 몫)
 *
 * <p>AFTER_COMMIT 단계에서만 실행되므로, 컨슈머가 메시지를 받는 시점에는 deployments row 가
 * 반드시 커밋되어 있다. 트랜잭션이 롤백되면 이 리스너는 아예 호출되지 않는다
 * → "DB엔 없는데 큐엔 있는" 유령 메시지가 생기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentEventListener {

    private final DeploymentPublisher deploymentPublisher;
    private final DeploymentLockManager lockManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeploymentRequested(DeploymentRequested event) {
        try {
            deploymentPublisher.publish(event);
        } catch (Exception e) {
            // 여기서 예외를 던져봐야 이미 커밋도 끝났고 HTTP 202 응답도 나간 뒤다.
            // 브로커가 죽어 있으면 배포는 QUEUED 로 남는다. (사용자는 취소 가능)
            // 상태머신상 QUEUED -> FAILED 전이가 없어서 FAILED 로 정리할 수는 없다.
            //
            // 다만 락은 반드시 풀어야 한다. 이 배포는 절대 진행되지 않으므로 종착 훅도 안 돌고,
            // 락을 쥔 채로 두면 TTL 이 만료될 때까지 그 프로젝트 전체가 배포 불가가 된다.
            log.error("failed to publish deploy request. deploymentId={} (stays QUEUED, releasing lock)",
                    event.deploymentId(), e);
            lockManager.unlock(event.projectId(), event.deploymentId());
        }
    }

    /**
     * 트랜잭션이 롤백되면 발행은 애초에 일어나지 않지만, <b>락은 Redis 에 남는다</b>
     * (락 획득은 DB 트랜잭션 바깥의 부수효과다). 여기서 되돌린다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onDeploymentRolledBack(DeploymentRequested event) {
        log.warn("deploy request transaction rolled back, releasing lock. deploymentId={}",
                event.deploymentId());
        lockManager.unlock(event.projectId(), event.deploymentId());
    }
}
