# 4주차 현수 과제 1번 — Deployment API 구현 가이드

> 작성일: 2026-06-16
> 대상: 4주차(2026-06-13 ~ 2026-06-19) 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **1번 (Deployment API)** 만
> 참조: `05_api_spec.md §4`, `04_state_machine.md`, `03_erd.md`
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.

---

## 1. 과제 범위 (이 가이드가 다루는 것)

`05_api_spec.md §4` 중 **CRUD 엔드포인트 4개**만 구현한다.

| # | 엔드포인트 | 상태/응답 | 명세 |
|---|---|---|---|
| ① | `POST /api/projects/{projectId}/deployments` | 202, `PENDING→QUEUED` | §4.1 |
| ② | `GET /api/projects/{projectId}/deployments?page=&size=` | 200 (페이지) | §4.2 |
| ③ | `GET /api/deployments/{deploymentId}` | 200 (단건) | §4.3 |
| ④ | `POST /api/deployments/{deploymentId}/cancel` | 200 | §4.4 |

### ⚠️ 과제 1에서 제외 (경계선)

- **RabbitMQ 실제 발행** → 과제 2. 과제 1은 **발행 seam(인터페이스 + 로그 스텁)** 까지만.
- **Redis 분산락** → 과제 5. 과제 1은 **DB 기반 in-progress 체크**로 `DEPLOYMENT_ALREADY_IN_PROGRESS`를 임시 처리.
- **롤백(§4.5)** → "4주차에 하지 말 것"에 따라 **5주차**. 지금 만들지 않음.
- **로그 스냅샷/SSE(§4.6~4.7)** → 과제 4. 여기선 X.

> 핵심: 과제 1은 "**Deployment 생성·조회·취소가 API로 동작**"까지. 실제 큐 발행·락은 다음 과제에서 이 seam에 꽂는다.

---

## 2. 이미 갖춰진 것 (재확인 완료 — 새로 만들 필요 없음)

- ✅ `Deployment` 엔티티 — 필요한 필드 전부 있음, `transitionTo()`가 상태머신 강제, builder 기본값 `PENDING`/`MANUAL`
- ✅ `DeploymentStatus` 상태머신 — `PENDING→{QUEUED,FAILED,CANCELED}`, `QUEUED→{CLONING,CANCELED}` → **취소는 PENDING/QUEUED에서만 가능**(과제 1 취소 규칙과 정확히 일치)
- ✅ `DeploymentRepository.findByProjectId(Long, Pageable)` — 이력 조회용 이미 존재
- ✅ `ErrorCode` — `DEPLOYMENT_NOT_FOUND(404)` / `DEPLOYMENT_ALREADY_IN_PROGRESS(409)` / `DEPLOYMENT_NOT_CANCELABLE(422)` 전부 정의됨
- ✅ 공통 패턴 — `ApiResponse.of` / `PagedResponse.of` / `AuthPrincipal.userId()` / `ApiException.of(code, field, value)`

→ **DB·enum·에러코드·공통 응답은 손댈 게 없다.** 컨트롤러/서비스/DTO 계층만 추가하면 된다.

---

## 3. 새로 만들 파일

```
deployment/
 ├ DeploymentController.java          # 신규
 ├ DeploymentService.java             # 신규
 ├ DeploymentPublisher.java           # 신규 (인터페이스 = 과제2 seam)
 ├ LoggingDeploymentPublisher.java    # 신규 (스텁 구현, log만 찍음)
 └ dto/
    ├ CreateDeploymentRequest.java    # { branch, commitHash } 둘 다 nullable
    ├ DeploymentDetailResponse.java   # §4.3 단건 (projectId, imageTag, failureReason 포함)
    └ DeploymentSummaryResponse.java  # §4.2 이력 행 (commitMessage, startedAt, finishedAt)
```

Repository에 **메서드 1개 추가** 필요(아래 §4-① 참조).

> DTO는 `ProjectResponse`처럼 **record + 정적 `from(Deployment)`** 패턴을 그대로 따른다.

---

## 4. 엔드포인트별 구현 지침

### ① POST `/api/projects/{projectId}/deployments` → 202

1. **소유권 검증**: `projectId`의 프로젝트가 현재 유저 소유인지 확인. → `ProjectService`의 `getOwnedProject` 로직을 **public 메서드로 노출**해 재사용 (중복 검증 코드 복붙 금지).
2. **중복 배포 가드**: 해당 project에 진행 중 배포가 있으면 `DEPLOYMENT_ALREADY_IN_PROGRESS`(409).
   - Repository에 `boolean existsByProjectIdAndStatusIn(Long projectId, Collection<DeploymentStatus> statuses)` 추가.
   - `statuses` = "진행 중" 집합. `DeploymentStatus`에 `isInProgress()`가 이미 있으니, 진행 중 상태 목록을 한 곳(상수 또는 enum 헬퍼)에서 산출.
   - 주석으로 *"과제 5에서 Redis 분산락으로 교체"* 명시.
3. **생성 & 전이**: `branch` 미지정 시 `project.getDefaultBranch()` 사용 → `Deployment.builder()`로 생성(`PENDING`) → `transitionTo(QUEUED)` → save.
4. **발행 seam**: `deploymentPublisher.publish(deployment)` 호출 (스텁은 log만). 트랜잭션 커밋 후 발행이 이상적이지만 **과제 1에선 스텁이라 무방** — 과제 2에서 `@TransactionalEventListener`/커밋 후 발행으로 다듬는다고 주석.
5. 응답: `ResponseEntity.status(202).body(ApiResponse.of(...))`, status는 `QUEUED`.

### ② GET `/api/projects/{projectId}/deployments` → 200 (페이지)

- 소유권 검증 후 `findByProjectId(projectId, pageable)`.
- 컨트롤러는 `@PageableDefault(size = 20)` + `PagedResponse.of(page)` (Project list와 동일).
- 정렬은 최신순(`created_at desc`) — `@PageableDefault(sort=..., direction=DESC)` 또는 Repository 쿼리에서. 이미 `idx_deployments_project_created` 인덱스 있음.

### ③ GET `/api/deployments/{deploymentId}` → 200 (단건)

- path에 projectId가 **없음** → `findById`로 deployment 로드 → `deployment.getProjectId()`로 프로젝트 로드 → 소유권 검증 → `DeploymentDetailResponse.from`.
- 없으면 `DEPLOYMENT_NOT_FOUND`(404).

### ④ POST `/api/deployments/{deploymentId}/cancel` → 200

- deployment 로드 + 소유권 검증.
- **취소 가능 판정**: 현재 상태가 `PENDING` 또는 `QUEUED`가 아니면 `DEPLOYMENT_NOT_CANCELABLE`(422).
  - 상태머신이 `BUILDING→CANCELED`를 막아주지만, **명세 에러코드(422)를 정확히 던지려면 transitionTo의 `IllegalStateException`에 의존하지 말고 서비스에서 먼저 명시적으로 체크**한다.
- `transitionTo(CANCELED)` → save → `{ deploymentId, status: "CANCELED" }`.

---

## 5. 반드시 재사용할 패턴 (일관성)

- 컨트롤러 시그니처: `@AuthenticationPrincipal AuthPrincipal principal` + `principal.userId()` (Project와 동일)
- 응답 래퍼: 단건 `ApiResponse.of`, 목록 `PagedResponse.of`
- 예외: `throw ApiException.of(ErrorCode.X, "field", value)` 또는 `new ApiException(ErrorCode.X)`
- 서비스 트랜잭션: 조회 `@Transactional(readOnly = true)`, 생성/취소 `@Transactional`
- DTO: `record` + 정적 `from(...)`

---

## 6. 과제 2·5와의 연결 seam (지금 박아둘 인터페이스)

```
DeploymentPublisher (interface)
   void publish(Deployment deployment)   // 과제2에서 RabbitTemplate 구현으로 교체
```

- 과제 1: `LoggingDeploymentPublisher`가 `log.info("publish deploy.queue id={}", ...)` 만.
- 중복 가드도 동일하게 *"Redis 락으로 교체 예정"* 주석을 남겨 과제 5 진입점을 명시.

이렇게 하면 과제 2·5는 **service 수정 없이 구현체만 교체**하면 된다.

---

## 7. 테스트 (과제 1 완료 판정)

- **단위**: 취소 가드 — `BUILDING` 등에서 cancel 시 `DEPLOYMENT_NOT_CANCELABLE`; in-progress 존재 시 POST가 409.
- **통합**(`ApiIntegrationTest` 스타일, H2): 로그인 → 프로젝트 생성 → POST deployment(202, QUEUED) → 동일 프로젝트 재요청(409) → 이력 GET(1건) → 단건 GET → cancel(200, CANCELED).
- 빌드 그린: 이 백엔드는 **JDK 21 + `./gradlew clean build`** (외부 DB 없이 H2로 컨텍스트 로드).

---

## 8. 완료 체크리스트

- [ ] 4개 엔드포인트가 `05_api_spec.md §4.1~4.4` 응답 포맷(`{data}` / `{data,page}`) 그대로 동작
- [ ] 에러: 409(중복) / 404(없음) / 422(취소불가) / 403(타인 프로젝트) 매핑
- [ ] `DeploymentPublisher` 인터페이스 + 로그 스텁으로 과제 2 seam 확보
- [ ] in-progress 가드 + "Redis 교체 예정" 주석으로 과제 5 seam 확보
- [ ] `./gradlew clean build` BUILD SUCCESSFUL, Swagger에 4개 노출

---

## 9. 하지 말 것

- RabbitMQ/Redis 의존성 추가·실제 연동 (과제 2·5)
- 롤백·SSE·Worker 파이프라인 (과제 3·4 / 5주차)
- `Deployment` 엔티티·상태머신·ErrorCode 변경 (이미 충분, 건드리면 3주차 테스트 회귀 위험)
