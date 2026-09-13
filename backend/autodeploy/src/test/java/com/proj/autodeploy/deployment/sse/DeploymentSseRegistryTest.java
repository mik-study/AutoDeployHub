package com.proj.autodeploy.deployment.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE fan-out 레지스트리 단위 테스트. 실제 HTTP 없이 {@link SseEmitter} 만 다룬다.
 */
class DeploymentSseRegistryTest {

    private static final Long DEPLOYMENT = 1L;

    private final DeploymentSseRegistry registry = new DeploymentSseRegistry();

    @Test
    @DisplayName("등록한 구독자에게 log 이벤트가 전달된다")
    void broadcastsLogToSubscribers() {
        RecordingEmitter emitter = new RecordingEmitter();
        registry.register(DEPLOYMENT, emitter);

        registry.broadcastLog(DEPLOYMENT, new DeploymentLogEvent(1L, "INFO", "Building image..."));

        assertThat(emitter.sentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("구독자가 여럿이면 전원에게 전달된다")
    void broadcastsToAllSubscribers() {
        RecordingEmitter a = new RecordingEmitter();
        RecordingEmitter b = new RecordingEmitter();
        registry.register(DEPLOYMENT, a);
        registry.register(DEPLOYMENT, b);

        registry.broadcastStatus(DEPLOYMENT, new DeploymentStatusEvent("BUILDING"));

        assertThat(a.sentCount()).isEqualTo(1);
        assertThat(b.sentCount()).isEqualTo(1);
        assertThat(registry.subscriberCount(DEPLOYMENT)).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 배포의 구독자에게는 전달되지 않는다")
    void doesNotLeakAcrossDeployments() {
        RecordingEmitter mine = new RecordingEmitter();
        RecordingEmitter other = new RecordingEmitter();
        registry.register(1L, mine);
        registry.register(2L, other);

        registry.broadcastLog(1L, new DeploymentLogEvent(1L, "INFO", "x"));

        assertThat(mine.sentCount()).isEqualTo(1);
        assertThat(other.sentCount()).isZero();
    }

    @Test
    @DisplayName("구독자가 없어도 브로드캐스트는 예외 없이 넘어간다")
    void broadcastWithoutSubscribersIsSafe() {
        assertThatNoException().isThrownBy(() ->
                registry.broadcastLog(DEPLOYMENT, new DeploymentLogEvent(1L, "INFO", "x")));
    }

    @Test
    @DisplayName("★ 끊긴 구독자는 목록에서 빠지고, 나머지 전송을 막지 않는다")
    void deadSubscriberIsRemovedAndDoesNotBlockOthers() {
        RecordingEmitter dead = new RecordingEmitter();
        dead.failOnSend = true;
        RecordingEmitter alive = new RecordingEmitter();
        registry.register(DEPLOYMENT, dead);
        registry.register(DEPLOYMENT, alive);

        registry.broadcastLog(DEPLOYMENT, new DeploymentLogEvent(1L, "INFO", "x"));

        // 살아있는 구독자는 정상 수신, 죽은 구독자는 정리됨
        assertThat(alive.sentCount()).isEqualTo(1);
        assertThat(registry.subscriberCount(DEPLOYMENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("closeAll 이후에는 구독자가 남지 않는다 (종착 시 연결 종료)")
    void closeAllClearsSubscribers() {
        registry.register(DEPLOYMENT, new RecordingEmitter());
        registry.register(DEPLOYMENT, new RecordingEmitter());

        registry.closeAll(DEPLOYMENT);

        assertThat(registry.subscriberCount(DEPLOYMENT)).isZero();
    }

    @Test
    @DisplayName("구독자가 없는 상태의 closeAll 도 안전하다")
    void closeAllWithoutSubscribersIsSafe() {
        assertThatNoException().isThrownBy(() -> registry.closeAll(DEPLOYMENT));
    }

    /** send 호출을 세고, 필요하면 끊긴 클라이언트처럼 IOException 을 던지는 테스트용 emitter. */
    private static class RecordingEmitter extends SseEmitter {

        private final List<Object> sent = new ArrayList<>();
        boolean failOnSend;

        RecordingEmitter() {
            super(60_000L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("broken pipe");
            }
            sent.add(builder);
        }

        @Override
        public void complete() {
            // 실제 SseEmitter.complete() 는 서블릿 비동기 컨텍스트를 요구하므로 no-op 으로 둔다.
        }

        int sentCount() {
            return sent.size();
        }
    }
}
