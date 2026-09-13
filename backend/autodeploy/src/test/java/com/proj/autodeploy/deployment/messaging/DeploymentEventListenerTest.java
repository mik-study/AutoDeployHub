package com.proj.autodeploy.deployment.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "커밋 후에만 발행된다"는 이번 과제의 핵심 계약을 못 박는 테스트.
 *
 * <p>DB 를 건드리지 않고 트랜잭션 경계와 이벤트 발행만 검증하므로 빠르고 안정적이다.
 * {@link DeploymentPublisher} 를 mock 으로 갈아끼워 RabbitMQ 브로커도 필요 없다.
 *
 * <p>이 클래스에는 {@code @Transactional} 을 붙이면 안 된다. 붙이는 순간 테스트 트랜잭션이 롤백돼서
 * AFTER_COMMIT 이 영원히 안 돌고, 지금 검증하려는 동작이 그대로 깨진다.
 */
@SpringBootTest
class DeploymentEventListenerTest {

    @MockitoBean DeploymentPublisher deploymentPublisher;

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션이 커밋되면 발행된다 — 커밋 전에는 발행되지 않는다")
    void publishesOnlyAfterCommit() {
        DeploymentRequested event = new DeploymentRequested(1L, 12L, "main", "abc1234");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            // 아직 트랜잭션 안 — 발행되면 안 된다
            verifyNoInteractions(deploymentPublisher);
        });

        // 커밋 후 — 이제 발행돼야 한다
        verify(deploymentPublisher).publish(event);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 발행되지 않는다 — 유령 메시지 방지")
    void doesNotPublishOnRollback() {
        DeploymentRequested event = new DeploymentRequested(2L, 12L, "main", null);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(deploymentPublisher);
    }
}
