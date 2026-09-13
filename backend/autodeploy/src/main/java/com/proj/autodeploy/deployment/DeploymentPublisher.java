package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.messaging.DeploymentRequested;

/**
 * 배포 작업 발행 seam.
 *
 * <p>과제 2에서 RabbitMQ 구현({@code RabbitDeploymentPublisher})으로 채웠다. 인터페이스를 유지하는
 * 이유는 테스트에서 브로커 없이 발행 여부만 검증하기 위해서다.
 *
 * <p>시그니처가 {@code Deployment} → {@code DeploymentRequested} 로 바뀐 이유: 발행은 트랜잭션 커밋
 * <b>이후</b>에 일어나므로 그 시점의 엔티티는 detached 다. 값만 담은 record 를 넘겨 경계를 명확히 한다.
 */
public interface DeploymentPublisher {

    void publish(DeploymentRequested message);
}
