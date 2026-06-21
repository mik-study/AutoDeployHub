package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;

/**
 * 배포 작업 발행 seam.
 *
 * <p>과제 1에서는 로그만 남기는 스텁({@link LoggingDeploymentPublisher})을 사용하고,
 * 과제 2에서 RabbitMQ(RabbitTemplate) 기반 구현으로 교체한다. (서비스 코드 수정 없이 구현체만 교체)
 */
public interface DeploymentPublisher {

    void publish(Deployment deployment);
}
