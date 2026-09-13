package com.proj.autodeploy.deployment.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * deploymentId → 구독 중인 {@link SseEmitter} 목록. (과제 4)
 *
 * <p>Worker 스레드가 로그를 적재할 때마다 여기로 fan-out 한다. Worker 와 SSE 구독자가 같은 JVM 에
 * 있다는 전제라서 in-memory 로 충분하다.
 *
 * <p>⚠️ <b>한계</b> — 백엔드를 여러 인스턴스로 스케일아웃하면, 다른 노드에서 도는 Worker 의 로그는
 * 이 레지스트리에 도달하지 않는다. 그때는 Redis Pub/Sub 같은 노드 간 브로드캐스트가 필요하다.
 * MVP(단일 노드) 범위 밖이라 도입하지 않았다.
 *
 * <p>자료구조 선택: 구독/해제보다 브로드캐스트가 압도적으로 잦아서 {@link CopyOnWriteArrayList} 를 쓴다.
 * 순회 중 다른 스레드가 구독을 추가/제거해도 {@code ConcurrentModificationException} 이 나지 않는다.
 */
@Slf4j
@Component
public class DeploymentSseRegistry {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 구독 등록. emitter 가 끝나거나(완료/타임아웃/에러) 하면 스스로 목록에서 빠진다.
     */
    public void register(Long deploymentId, SseEmitter emitter) {
        emitters.computeIfAbsent(deploymentId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        // 정리 콜백을 안 걸면 끊긴 emitter 가 계속 쌓여서 브로드캐스트마다 IOException 을 유발한다.
        emitter.onCompletion(() -> remove(deploymentId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();                 // onCompletion 이 이어서 호출된다
        });
        emitter.onError(e -> remove(deploymentId, emitter));
    }

    /** 신규 로그 브로드캐스트. {@code id:} 에 sequence 를 실어 Last-Event-ID 재연결을 지원한다. */
    public void broadcastLog(Long deploymentId, DeploymentLogEvent event) {
        send(deploymentId, SseEmitter.event()
                .id(String.valueOf(event.sequence()))
                .name("log")
                .data(event));
    }

    /**
     * 상태 전이 브로드캐스트.
     *
     * <p>{@code id:} 를 싣지 않는다. status 이벤트에 sequence 를 붙이면 클라이언트의 Last-Event-ID 가
     * 로그 sequence 와 뒤섞여, 재연결 시 백필 기준점이 틀어진다.
     */
    public void broadcastStatus(Long deploymentId, DeploymentStatusEvent event) {
        send(deploymentId, SseEmitter.event().name("status").data(event));
    }

    /**
     * 종착 도달 → {@code close} 이벤트 후 서버에서 연결을 끊는다. (명세 §4.7)
     *
     * <p>서버가 먼저 complete 하지 않으면 브라우저 {@code EventSource} 가 끊긴 걸 오류로 보고
     * 자동 재연결을 반복한다.
     */
    public void closeAll(Long deploymentId) {
        List<SseEmitter> subscribers = emitters.remove(deploymentId);
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("close").data(Map.of()));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                // 이미 끊긴 클라이언트. 어차피 닫는 중이라 조용히 넘어간다.
                emitter.complete();
            }
        }
    }

    /** 구독자 수. (테스트/진단용) */
    public int subscriberCount(Long deploymentId) {
        return emitters.getOrDefault(deploymentId, List.of()).size();
    }

    private void send(Long deploymentId, SseEmitter.SseEventBuilder event) {
        List<SseEmitter> subscribers = emitters.get(deploymentId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                // 클라이언트가 끊었거나 emitter 가 이미 완료됨. 한 구독자의 실패가 나머지 전송이나
                // Worker 파이프라인을 막으면 안 되므로 여기서 삼키고 정리만 한다.
                log.debug("dropping dead SSE subscriber. deploymentId={}", deploymentId);
                remove(deploymentId, emitter);
            }
        }
    }

    private void remove(Long deploymentId, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(deploymentId);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(emitter);
        // 빈 리스트를 남겨두면 배포 수만큼 맵이 계속 커진다.
        emitters.computeIfPresent(deploymentId, (id, list) -> list.isEmpty() ? null : list);
    }
}
