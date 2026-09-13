package com.proj.autodeploy.deployment.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * deploy 큐 토폴로지 선언.
 *
 * <p>여기 선언한 Queue/Exchange/Binding 빈은 RabbitAdmin 이 브로커 접속 시점에 자동으로 declare 한다.
 * 즉 management UI 에서 손으로 큐를 만들 필요가 없다.
 */
@Configuration
public class RabbitConfig {

    @Bean
    Queue deployQueue() {
        return QueueBuilder.durable(DeployQueueConstants.QUEUE).build();
    }

    @Bean
    DirectExchange deployExchange() {
        // (name, durable, autoDelete)
        return new DirectExchange(DeployQueueConstants.EXCHANGE, true, false);
    }

    @Bean
    Binding deployBinding(Queue deployQueue, DirectExchange deployExchange) {
        return BindingBuilder.bind(deployQueue)
                .to(deployExchange)
                .with(DeployQueueConstants.ROUTING_KEY);
    }

    /**
     * 기본 컨버터는 SimpleMessageConverter = 자바 직렬화라서 management UI 에서 본문이 안 읽힌다.
     * JSON 컨버터를 빈으로 등록해두면 Boot 자동설정이 RabbitTemplate 과 리스너 컨테이너 양쪽에
     * 알아서 적용해준다.
     *
     * <p>Spring AMQP 4.x(Boot 4) 기준 클래스명은 {@code JacksonJsonMessageConverter} 다.
     * 검색으로 나오는 {@code Jackson2JsonMessageConverter} 는 Jackson 2 시절 이름으로, 지금은 deprecated.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
