package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * "어떻게 발행하는가"를 담당. ("언제 발행하는가"는 {@link DeploymentEventListener} 의 몫)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitDeploymentPublisher implements DeploymentPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(DeploymentRequested message) {
        rabbitTemplate.convertAndSend(
                DeployQueueConstants.EXCHANGE,
                DeployQueueConstants.ROUTING_KEY,
                message);
        log.info("published deploy request: deploymentId={}, projectId={}, branch={}",
                message.deploymentId(), message.projectId(), message.branch());
    }
}
