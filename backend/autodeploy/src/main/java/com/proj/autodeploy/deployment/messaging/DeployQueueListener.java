package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentLifecycleService;
import com.proj.autodeploy.deployment.DeploymentWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * deploy.queue 컨슈머. 짧은 상태 전이 트랜잭션과 긴 파이프라인 실행을 분리한다.
 *
 * <p>{@code startProcessing} 을 별도 빈({@link DeploymentLifecycleService})에 둔 이유는
 * 같은 클래스 안의 {@code @Transactional} 메서드를 self-invocation 하면 프록시를 안 거쳐
 * 트랜잭션이 걸리지 않기 때문이다.
 *
 * <p>{@code auto-startup=false}(테스트 프로파일) 일 때는 이 빈 자체를 만들지 않는다.
 * Spring AMQP 의 {@code RabbitListenerEndpointRegistry.startIfNecessary()} 는
 * {@code contextRefreshed || container.isAutoStartup()} 조건이라, 컨텍스트 refresh 이후
 * registry 가 다시 start 되면 auto-startup=false 를 무시하고 컨테이너를 띄운다.
 * 엔드포인트를 아예 등록하지 않는 편이 확실하다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DeployQueueListener {

    private final DeploymentLifecycleService lifecycleService;
    private final DeploymentWorker deploymentWorker;

    @RabbitListener(queues = DeployQueueConstants.QUEUE)
    public void onDeploymentRequested(DeploymentRequested message) {
        Long deploymentId = message.deploymentId();
        log.info("received deploy request: deploymentId={}", deploymentId);

        // 1) 멱등성 가드 + QUEUED -> CLONING. 여기까지가 짧은 트랜잭션.
        //    CLONING 전이를 가장 먼저 하는 이유: 상태머신상 QUEUED 에서는 FAILED 로 갈 수 없어서,
        //    이후 무슨 일이 생겨도 FAILED 로 정리하려면 일단 CLONING 까지 올려놓아야 한다.
        if (!lifecycleService.startProcessing(deploymentId)) {
            return;   // 정상 ack. 재큐잉하지 않는다.
        }

        // 2) 파이프라인 본체는 트랜잭션 밖에서. (과제 3에서 수 분 걸릴 수 있음)
        try {
            deploymentWorker.run(deploymentId);
        } catch (Exception e) {
            log.error("deploy pipeline failed. deploymentId={}", deploymentId, e);
            lifecycleService.markFailed(deploymentId, e.getMessage());
            // 예외를 다시 던지지 않는다. DLQ 가 없는 상태에서 던지면
            // 브로커가 같은 메시지를 계속 재전달해 무한 루프가 된다.
        }
    }
}
