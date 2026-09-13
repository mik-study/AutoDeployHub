package com.proj.autodeploy.deployment.messaging;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitDeploymentPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks RabbitDeploymentPublisher publisher;

    @Test
    @DisplayName("약속된 exchange/routingKey 로 메시지를 발행한다")
    void publishesToConfiguredExchangeAndRoutingKey() {
        DeploymentRequested message = new DeploymentRequested(1L, 12L, "main", "abc1234");

        publisher.publish(message);

        // (Object) 캐스팅: convertAndSend 오버로드가 많아 어느 시그니처인지 명시해준다
        verify(rabbitTemplate).convertAndSend(
                DeployQueueConstants.EXCHANGE,
                DeployQueueConstants.ROUTING_KEY,
                (Object) message);
    }
}
