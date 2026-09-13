package com.proj.autodeploy.deployment.messaging;

/**
 * deploy 큐 토폴로지 상수.
 *
 * <p>발행부({@link RabbitDeploymentPublisher})와 수신부({@link DeployQueueListener})가 반드시 같은
 * 문자열을 참조하도록 한 곳에서만 정의한다. 라우팅 키 오타는 예외 없이 "메시지가 조용히 사라지는"
 * 형태로 나타나기 때문에 상수화가 사실상 필수다.
 */
public final class DeployQueueConstants {

    /** 배포 요청 큐. durable = 브로커가 재시작해도 큐 정의가 남는다. */
    public static final String QUEUE = "deploy.queue";

    /** direct exchange = 라우팅 키가 "정확히 일치"하는 바인딩으로만 전달된다. */
    public static final String EXCHANGE = "deploy.exchange";

    /** deploy.queue 로 향하는 라우팅 키. */
    public static final String ROUTING_KEY = "deploy.request";

    private DeployQueueConstants() {
    }
}
