# 4주차 현수 과제 1번 — Deployment API 구현 변경내역

> 가이드(`4주차 현수 과제 1번 — Deployment API 구현 가이드`)에 따른 구현 결과.
>
> - 작성일: 2026-06-16
> - 담당: 김현수
> - 범위: `05_api_spec.md §4.1~4.4` (CRUD 4개). RabbitMQ 실제 발행/Redis 락/롤백/SSE는 제외(과제 2·5·3·4)
> - 빌드: `./gradlew clean build` → ✅ **BUILD SUCCESSFUL** (기존 + 신규 Deployment 테스트 통과)

---

## 1. 한 줄 요약

3주차에 만든 **Deployment 엔티티 + DeploymentStatus 상태머신** 위에 배포 **생성·이력·단건·취소** API를 추가했다.
실제 큐 발행과 분산락은 **seam(인터페이스/스텁 + DB 기반 임시 가드)** 으로 박아두어, 과제 2·5에서 **서비스 수정 없이 구현체만 교체**하면 된다.

---

## 2. 신규 파일 (8개)

| 파일 | 설명 |
|---|---|
| `deployment/DeploymentController.java` | 엔드포인트 4개. 경로가 `/api/projects/{id}/deployments` 와 `/api/deployments/{id}` 두 갈래라 메서드별 전체 경로 사용 |
| `deployment/DeploymentService.java` | `create` / `list` / `get` / `cancel` 비즈니스 로직 |
| `deployment/DeploymentPublisher.java` | **발행 seam 인터페이스** (과제 2 진입점) |
| `deployment/LoggingDeploymentPublisher.java` | 로그만 남기는 **스텁 구현** (`@Component`) |
| `deployment/dto/CreateDeploymentRequest.java` | `{ branch, commitHash }` 둘 다 nullable |
| `deployment/dto/DeploymentDetailResponse.java` | §4.3 단건. 생성(§4.1)·취소(§4.4) 응답에도 재사용 |
| `deployment/dto/DeploymentSummaryResponse.java` | §4.2 이력 목록 행 |
| `test/api/DeploymentApiTest.java` | 통합 테스트 2개 (E2E + 취소 422) |

## 3. 수정 파일 (2개)

| 파일 | 변경 |
|---|---|
| `deployment/DeploymentRepository.java` | `boolean existsByProjectIdAndStatusIn(Long, Collection<DeploymentStatus>)` 추가 (중복 배포 가드용) |
| `project/ProjectService.java` | `getOwnedProject(userId, projectId)` **private → public** (deployment에서 소유권 검증 재사용, 중복 코드 방지) |

> 엔티티 / 상태머신 / ErrorCode / 공통 응답(ApiResponse·PagedResponse)은 **변경 없음** (이미 충분, 3주차 테스트 회귀 방지).

---

## 4. 엔드포인트 동작

| # | 엔드포인트 | 처리 흐름 | 상태 |
|---|---|---|---|
| ① | `POST /api/projects/{projectId}/deployments` | 소유권 검증 → 중복 가드(진행 중 배포 존재 시 409) → `branch` 기본값 처리 → `Deployment(PENDING)` 생성 → `transitionTo(QUEUED)` → save → `publisher.publish()`(스텁) | **202** |
| ② | `GET /api/projects/{projectId}/deployments` | 소유권 검증 → `findByProjectId` 최신순 페이지(`@PageableDefault sort=createdAt DESC`) | **200** |
| ③ | `GET /api/deployments/{deploymentId}` | `findById` → 소속 프로젝트로 소유권 검증 → 상세 | **200** (없으면 404) |
| ④ | `POST /api/deployments/{deploymentId}/cancel` | 소유권 검증 → `PENDING`/`QUEUED` 아니면 422 → `transitionTo(CANCELED)` | **200** |

### 에러 매핑
- `DEPLOYMENT_ALREADY_IN_PROGRESS` (409) — 동일 프로젝트 진행 중 배포 존재
- `DEPLOYMENT_NOT_FOUND` (404) — 없는 배포
- `DEPLOYMENT_NOT_CANCELABLE` (422) — 취소 불가 상태(BUILDING 이후 등)
- `PROJECT_ACCESS_DENIED` (403) — 타인 프로젝트 (소유권 검증 재사용)

---

## 5. 과제 2·5 연결 seam (지금 박아둔 확장점)

### 과제 2 (RabbitMQ 발행)
- `DeploymentPublisher` 인터페이스 + `LoggingDeploymentPublisher`(로그 스텁).
- `DeploymentService.create`에 `// TODO(과제 2): 트랜잭션 커밋 후 발행(@TransactionalEventListener)으로 다듬기` 주석.
- → 과제 2는 `RabbitTemplate` 기반 구현체를 추가하고 빈만 교체하면 끝.

### 과제 5 (Redis 분산락)
- 중복 가드를 **DB 기반** `existsByProjectIdAndStatusIn(IN_PROGRESS_STATUSES)` 로 임시 처리.
- `IN_PROGRESS_STATUSES` 는 `DeploymentStatus.isInProgress()` 기준으로 서비스에서 한 번만 산출(상태머신 미변경).
- `// TODO(과제 5): Redis 분산락으로 교체` 주석.

---

## 6. 테스트 / 검증

- **통합 테스트** (`DeploymentApiTest`, H2):
  - `deploymentLifecycle`: 로그인 → 프로젝트 생성 → 배포 요청(202, QUEUED) → 동일 프로젝트 재요청(409) → 이력(1건) → 단건 조회 → 취소(200, CANCELED)
  - `cancelNonCancelable`: 이미 CANCELED 상태에서 재취소 → 422
- `./gradlew clean build` → **BUILD SUCCESSFUL** (외부 DB 없이 H2 컨텍스트 로드).
- 재기동 시 Swagger(`/swagger-ui/index.html`)에 4개 엔드포인트 자동 노출.

---

## 7. 완료 체크리스트 (가이드 §8 대비)

- [x] 4개 엔드포인트가 §4.1~4.4 응답 포맷(`{data}` / `{data,page}`) 그대로 동작
- [x] 에러 409 / 404 / 422 / 403 매핑
- [x] `DeploymentPublisher` 인터페이스 + 로그 스텁으로 과제 2 seam 확보
- [x] in-progress 가드 + "Redis 교체 예정" 주석으로 과제 5 seam 확보
- [x] `./gradlew clean build` BUILD SUCCESSFUL
