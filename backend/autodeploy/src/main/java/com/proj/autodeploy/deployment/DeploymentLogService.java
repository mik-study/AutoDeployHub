package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentLog;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.LogLevel;
import com.proj.autodeploy.deployment.dto.LogEntryResponse;
import com.proj.autodeploy.deployment.dto.LogSnapshotResponse;
import com.proj.autodeploy.deployment.sse.DeploymentLogEvent;
import com.proj.autodeploy.deployment.sse.DeploymentSseRegistry;
import com.proj.autodeploy.deployment.sse.DeploymentStatusEvent;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.ProjectService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 배포 로그 스냅샷 조회 + SSE 스트림. (05_api_spec.md §4.6~4.7, 과제 4)
 *
 * <p>로그 적재({@link #append})도 여기 둔다. 적재와 실시간 push 가 한 덩어리여야
 * "DB 에는 있는데 스트림에는 안 온 로그"가 생기지 않기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentLogService {

    /** 배포가 길어져도 스트림이 먼저 끊기지 않도록 넉넉히. 락 TTL(30분)과 맞춰 둔다. */
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private static final int MAX_LIMIT = 1000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final ProjectService projectService;
    private final DeploymentSseRegistry sseRegistry;

    // ------------------------------------------------------------------
    // 조회 (외부 API)
    // ------------------------------------------------------------------

    /** §4.6 스냅샷 조회. */
    @Transactional(readOnly = true)
    public LogSnapshotResponse snapshot(Long userId, Long deploymentId, Long fromSequence, int limit) {
        getOwnedDeployment(userId, deploymentId);

        int capped = Math.clamp(limit, 1, MAX_LIMIT);
        long from = (fromSequence != null && fromSequence > 0) ? fromSequence : 0L;

        // limit+1 개를 가져와서, 초과분이 있으면 hasMore=true 로 판정하고 응답에서는 잘라낸다.
        // count 쿼리를 따로 날리지 않아도 되는 흔한 기법.
        List<DeploymentLog> found = deploymentLogRepository
                .findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(
                        deploymentId, from, PageRequest.of(0, capped + 1));

        boolean hasMore = found.size() > capped;
        List<DeploymentLog> page = hasMore ? found.subList(0, capped) : found;

        // 빈 페이지면 다음 요청도 같은 지점에서 시작해야 한다 (from 을 그대로 돌려준다).
        long nextFromSequence = page.isEmpty()
                ? from
                : page.getLast().getSequence() + 1;

        return new LogSnapshotResponse(
                page.stream().map(LogEntryResponse::from).toList(),
                nextFromSequence,
                hasMore);
    }

    /**
     * §4.7 실시간 스트림 구독.
     *
     * @param lastEventId 클라이언트의 {@code Last-Event-ID} 헤더. 재연결이면 마지막으로 받은 sequence
     */
    @Transactional(readOnly = true)
    public SseEmitter stream(Long userId, Long deploymentId, String lastEventId) {
        Deployment deployment = getOwnedDeployment(userId, deploymentId);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        // 백필보다 먼저 등록한다. 반대 순서면 "백필 완료 ~ 등록" 사이에 적재된 로그를 놓친다.
        // 이 순서에서는 그 구간의 로그가 중복 전송될 수 있는데, 클라이언트가 sequence 로
        // 걸러낼 수 있으므로 누락보다 낫다.
        sseRegistry.register(deploymentId, emitter);

        try {
            backfill(emitter, deploymentId, lastEventId);
            // 현재 상태를 한 번 흘려보내 클라이언트가 진행 단계를 즉시 그릴 수 있게 한다.
            emitter.send(SseEmitter.event().name("status")
                    .data(new DeploymentStatusEvent(deployment.getStatus().name())));
        } catch (IOException | IllegalStateException e) {
            // 백필 도중 클라이언트가 끊음. 스트림만 접고 요청은 정상 종료시킨다.
            log.debug("client disconnected during backfill. deploymentId={}", deploymentId);
            emitter.complete();
            return emitter;
        }

        // 이미 끝난 배포면 더 올 게 없다 → close 후 즉시 종료. (명세 §4.7)
        if (deployment.getStatus().isTerminal()) {
            sseRegistry.closeAll(deploymentId);
        }
        return emitter;
    }

    // ------------------------------------------------------------------
    // 적재 (Worker 전용 - 과제 3 에서 호출)
    // ------------------------------------------------------------------

    /**
     * 로그 한 줄 적재 + 실시간 push.
     *
     * <p>sequence 는 {@code max(sequence)+1} 로 매긴다. 한 배포는 컨슈머 스레드 하나가 처리하고
     * ({@code listener.simple.concurrency=1}, 프로젝트 단위 분산락), 같은 deploymentId 로 동시에
     * 적재하는 경로가 없어서 이 방식으로 충분하다.
     *
     * <p>SSE 전송은 <b>DB 저장 뒤</b>에 한다. 순서가 반대면 스트림으로 본 로그를 새로고침 후
     * 스냅샷에서 못 찾는 상황이 생긴다.
     */
    @Transactional
    public DeploymentLog append(Long deploymentId, LogLevel level, String message) {
        long nextSequence = deploymentLogRepository
                .findTopByDeploymentIdOrderBySequenceDesc(deploymentId)
                .map(last -> last.getSequence() + 1)
                .orElse(1L);

        DeploymentLog saved = deploymentLogRepository.save(DeploymentLog.builder()
                .deploymentId(deploymentId)
                .sequence(nextSequence)
                .level(level)
                .message(message)
                .build());

        sseRegistry.broadcastLog(deploymentId, DeploymentLogEvent.from(saved));
        return saved;
    }

    /** INFO 레벨 단축. */
    @Transactional
    public DeploymentLog info(Long deploymentId, String message) {
        return append(deploymentId, LogLevel.INFO, message);
    }

    /**
     * 상태 전이 push. 종착이면 {@code close} 이벤트까지 보내고 연결을 끊는다.
     *
     * <p>DB 상태 전이는 {@link DeploymentLifecycleService} 가 담당한다 — 여기서는 알림만 한다.
     */
    public void publishStatus(Long deploymentId, DeploymentStatus status) {
        sseRegistry.broadcastStatus(deploymentId, new DeploymentStatusEvent(status.name()));
        if (status.isTerminal()) {
            sseRegistry.closeAll(deploymentId);
        }
    }

    // ------------------------------------------------------------------

    private void backfill(SseEmitter emitter, Long deploymentId, String lastEventId) throws IOException {
        long from = parseLastEventId(lastEventId);
        List<DeploymentLog> missed = deploymentLogRepository
                .findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(deploymentId, from);
        for (DeploymentLog entry : missed) {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(entry.getSequence()))
                    .name("log")
                    .data(DeploymentLogEvent.from(entry)));
        }
    }

    /** {@code Last-Event-ID} 다음 sequence 부터. 헤더가 없거나 이상하면 처음부터 보낸다. */
    private long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(lastEventId.trim()) + 1;
        } catch (NumberFormatException e) {
            log.warn("unparsable Last-Event-ID '{}', replaying from the beginning", lastEventId);
            return 0L;
        }
    }

    /** deployment 로드 + (소속 프로젝트 기준) 소유권 검증. */
    private Deployment getOwnedDeployment(Long userId, Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.DEPLOYMENT_NOT_FOUND, "deploymentId", deploymentId));
        projectService.getOwnedProject(userId, deployment.getProjectId());
        return deployment;
    }
}
