# 4주차 현수 과제 4번 — SSE 실시간 로그 가이드

> 작성일: 2026-06-16
> 대상: 4주차 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **4번 (SSE 실시간 로그)** 만
> 참조: `05_api_spec.md §4.6~4.7`, 과제 3의 로그 적재 훅
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.

---

## 1. 과제 범위

배포 진행 로그를 **(a) 스냅샷 조회** + **(b) 실시간 SSE 스트림** 두 경로로 노출.

| # | 엔드포인트 | 응답 | 명세 |
|---|---|---|---|
| ① | `GET /api/deployments/{id}/logs?fromSequence=0&limit=200` | 200, `{data, nextFromSequence, hasMore}` | §4.6 |
| ② | `GET /api/deployments/{id}/logs/stream` (`text/event-stream`) | `log`/`status`/`close` 이벤트 | §4.7 |

---

## 2. 사전 자산 (이미 있음)

- ✅ `DeploymentLog` 엔티티(`deploymentId`, `sequence`, `level`, `message`, `createdAt`)
- ✅ `DeploymentLogRepository.findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(id, fromSequence)` → **스냅샷·재연결 조회에 그대로 사용**
- ✅ 과제 3 Worker가 각 단계에서 `DeploymentLog` 적재 → 여기에 **실시간 push 훅**을 얹는다.
- ⚙️ SSE는 Spring MVC `SseEmitter`로 충분(추가 의존성 불필요). webmvc 이미 있음.

---

## 3. 새로 만들 파일

```
deployment/
 ├ DeploymentLogController.java       # ①② 엔드포인트
 ├ DeploymentLogService.java          # 스냅샷 조회 + emitter 등록/조회
 ├ sse/
 │   ├ DeploymentSseRegistry.java     # deploymentId → List<SseEmitter> 보관 (in-memory)
 │   └ DeploymentLogEvent.java        # 내부 이벤트(로그/상태) record
 └ dto/
     ├ LogSnapshotResponse.java       # {data, nextFromSequence, hasMore}
     └ LogEntryResponse.java          # {sequence, level, message, createdAt}
```

---

## 4. 구현 지침

### 4.1 스냅샷 (`GET .../logs`) — §4.6
- `fromSequence`(기본 0), `limit`(기본 200) 쿼리 파라미터.
- Repository로 `fromSequence` 이상 조회 → `limit+1`개 가져와 `hasMore` 판정, 마지막 `sequence+1`을 `nextFromSequence`로.
- 응답: `{ data: [...], nextFromSequence, hasMore }` (명세 그대로). `data`는 `LogEntryResponse` 목록.
- 소유권 검증: deployment → project → owner (과제 1과 동일 패턴 재사용).

### 4.2 실시간 스트림 (`GET .../logs/stream`) — §4.7
- 컨트롤러가 `SseEmitter`(timeout 충분히 길게) 반환, `Content-Type: text/event-stream`.
- 흐름:
  1. **소유권 검증.**
  2. `Last-Event-ID` 헤더(없으면 `fromSequence=0`) 기준 **누락분 백필**: DB 스냅샷을 `log` 이벤트로 먼저 흘려보냄(재연결 시 빈 구간 방지).
  3. emitter를 `DeploymentSseRegistry`에 `deploymentId` 키로 등록.
  4. 이후 Worker가 발생시키는 신규 로그/상태를 실시간 전송.
  5. deployment가 `isTerminal()` 도달 → `status` 이벤트(최종) → `close` 이벤트 → `emitter.complete()`.
- **이벤트 포맷**(명세 §4.7):
  - `event: log` / `data: {"sequence","level","message"}`
  - `event: status` / `data: {"status"}`
  - `event: close` / `data: {}`
- **`id:` 필드에 `sequence`를 실어** 보내 클라이언트 `Last-Event-ID` 재연결이 동작하게 한다.

### 4.3 Worker → SSE 연결 (in-memory fan-out)
- 4주차는 **단일 모듈/단일 JVM** (Worker가 같은 프로세스). → `ApplicationEventPublisher` 또는 `DeploymentSseRegistry` 직접 호출로 fan-out 가능.
- 과제 3의 "로그 적재 훅"에서:
  - `DeploymentLog` 저장 → `registry.broadcast(deploymentId, logEvent)`
  - `transitionTo()` 직후 → `registry.broadcast(deploymentId, statusEvent)`; 종착이면 `close` + complete.
- 이미 종료된 배포를 stream 요청하면 → 스냅샷 전부 전송 후 즉시 `close`.

> ⚠️ 멀티 인스턴스로 스케일아웃하면 in-memory 레지스트리로는 다른 노드의 로그를 못 받음 → Redis Pub/Sub 필요. **MVP(단일 노드)에서는 범위 밖**. 주석으로 한계 명시.

### 4.4 보안 (공통 모임 안건)
- 브라우저 `EventSource`는 **커스텀 `Authorization` 헤더를 못 붙인다.** → 다음 중 택1을 민준과 합의:
  - 쿼리 파라미터 토큰(`?access_token=`) + 필터에서 파싱, 또는
  - 쿠키 기반 인증.
- `SecurityConfig`에 stream 경로의 인증 방식 반영 필요. **모임에서 확정**.

---

## 5. 연결축

- 과제 3: 로그 적재/상태 전이 훅이 곧 SSE 소스.
- 민준 과제 3(프론트 EventSource 뷰어): 이벤트 포맷·`Last-Event-ID` 재연결·인증 방식이 계약. 모임에서 §4.7 포맷 합의.

---

## 6. 테스트

- **스냅샷**: 로그 N개 적재 후 `fromSequence`/`limit` 페이징, `hasMore`/`nextFromSequence` 경계.
- **스트림**: MockMvc async 또는 `WebTestClient`로 `text/event-stream` 수신, 종착 시 `close` 후 완료 확인.
- 재연결: `Last-Event-ID` 지정 시 그 이후 sequence만 오는지.

## 7. 완료 체크리스트

- [ ] 스냅샷 응답이 §4.6 포맷(`data/nextFromSequence/hasMore`) 준수
- [ ] 스트림이 `log`/`status`/`close` 이벤트 + `id:`=sequence 전송
- [ ] 종착(`isTerminal`) 시 `close` 후 서버에서 연결 종료
- [ ] `Last-Event-ID` 재연결 시 누락분 백필
- [ ] EventSource 인증 방식 민준과 합의·SecurityConfig 반영
- [ ] `./gradlew clean build` BUILD SUCCESSFUL

## 8. 하지 말 것

- Redis Pub/Sub 기반 멀티노드 fan-out (MVP 단일 노드 범위 밖)
- WebSocket 전환 (SSE로 충분)
- 로그 영속 스키마 변경 (`DeploymentLog` 그대로 사용)
