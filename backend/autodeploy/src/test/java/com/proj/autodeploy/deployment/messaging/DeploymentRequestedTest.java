package com.proj.autodeploy.deployment.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * 메시지 계약이 JSON 으로 온전히 왕복하는지 검증. 브로커 없이 컨버터만 단위 테스트한다.
 */
class DeploymentRequestedTest {

    private final MessageConverter converter = new JacksonJsonMessageConverter();

    @Test
    @DisplayName("직렬화 → 역직렬화 라운드트립에서 값이 보존된다")
    void roundTrip() {
        DeploymentRequested original = new DeploymentRequested(1L, 12L, "main", "abc1234");

        Message message = converter.toMessage(original, new MessageProperties());
        Object restored = converter.fromMessage(message);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("content-type 이 application/json 이다 (management UI 에서 본문이 읽힌다)")
    void contentTypeIsJson() {
        Message message = converter.toMessage(
                new DeploymentRequested(1L, 12L, "main", null), new MessageProperties());

        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    @DisplayName("commitHash 가 null 이어도 왕복된다 (수동 배포는 commitHash 없이 요청된다)")
    void nullCommitHash() {
        DeploymentRequested original = new DeploymentRequested(1L, 12L, "main", null);

        Object restored = converter.fromMessage(
                converter.toMessage(original, new MessageProperties()));

        assertThat(restored).isEqualTo(original);
    }
}
