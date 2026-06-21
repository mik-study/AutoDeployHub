package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 과제 1용 스텁 publisher. 실제 큐 발행 대신 로그만 남긴다.
 * 과제 2에서 RabbitMQ 구현체로 교체된다.
 */
@Slf4j
@Component
public class LoggingDeploymentPublisher implements DeploymentPublisher {

    @Override
    public void publish(Deployment deployment) {
        log.info("[STUB] publish to deploy.queue - deploymentId={}, projectId={}, branch={}",
                deployment.getId(), deployment.getProjectId(), deployment.getBranch());
    }
}
