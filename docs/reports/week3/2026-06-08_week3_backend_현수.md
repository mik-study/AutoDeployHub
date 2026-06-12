# 3주차 백엔드 진행 보고서 (현수)

> AutoDeployHub — 3주차 첫 코딩 스프린트 중 **백엔드 도메인 & 인증** 파트 결과.
>
> - 작성일: 2026-06-08
> - 담당: 김현수
> - 대상 모듈: `backend/autodeploy`
> - 빌드 결과: `./gradlew clean build` → ✅ **BUILD SUCCESSFUL** (테스트 포함)

---

## 1. 한 줄 요약

ERD(03)·상태머신(04)·API 명세(05) 설계 문서를 **실제 동작하는 Spring Boot(Java 21) 코드**로 구현 완료.
7개 엔티티 + enum, JWT 인증(4종), Project CRUD(5종), 상태머신 단위 테스트까지 빌드 그린.
실제 DB/MQ 기동이 필요한 통합 부분만 민준의 `08_docker-compose.yml` 완료 후 이어서 진행.

---

## 2. 이번에 결정한 것 (회의 안건 처리)

| 안건 | 결정 | 사유 |
|---|---|---|
| 모듈 구조 | **단일 모듈 유지** (feature 패키지로 경계) | 2인 MVP에 멀티모듈은 과함. Worker 분리 필요 시점(4~5주차) 재검토 |
| 스키마 관리 | **`ddl-auto=update`** (로컬 dev) | 스키마 변동기엔 Flyway 운영비용 큼. 안정화 후 Flyway 도입 |
| 백엔드 언어 | **Java 21** 확정 | Virtual Thread 활용. (초기 Kotlin 스캐폴드 → Java 전환 완료) |
| 테스트 DB | **H2(PostgreSQL 호환 모드)** | 외부 Docker 없이 CI/로컬에서 컨텍스트 로드·테스트 가능 |

---

## 3. 구현 산출물

### 3.1 도메인 엔티티 (7종) — `03_erd.md` 1:1 반영

| 엔티티 | 테이블 | 비고 |
|---|---|---|
| `User` | `users` | email unique, BCrypt 해시, status enum |
| `Project` | `projects` | subdomain unique, owner_id index, webhook_secret, health check 설정 |
| `EnvironmentVariable` | `environment_variables` | (project_id, env_key) unique, 암호화 값 컬럼 |
| `Deployment` | `deployments` | 상태머신 보유, previous_deployment_id, trigger_type |
| `DeploymentLog` | `deployment_logs` | (deployment_id, sequence) index, level |
| `RuntimeInstance` | `runtime_instances` | **Blue-Green: color/port/is_active**, container_name unique |
| `Webhook` | `webhooks` | delivery_id unique(중복 수신 방지), signature_valid/processed |

- 인덱스/유니크 제약은 ERD §3의 SQL 기준으로 `@Index` / `@Column(unique=true)` 로 반영.
- 생성/수정 시각은 Hibernate `@CreationTimestamp` / `@UpdateTimestamp` 사용(별도 설정 불필요).
- `key` 가 일부 DB 예약어라 환경변수 컬럼명은 `env_key` 로 매핑.

### 3.2 enum (8종)

`DeploymentStatus`(15 상태) · `DeploymentTriggerType` · `LogLevel` · `RuntimeColor`(BLUE/GREEN, `opposite()`/`defaultPort()`) · `RuntimeStatus` · `ProjectStatus` · `BuildType` · `UserStatus`

### 3.3 상태머신 — `04_state_machine.md` 구현

- `DeploymentStatus.canTransitionTo(next)` 로 전이 규칙을 코드로 강제(전이표를 `EnumMap`으로 보유).
- `Deployment.transitionTo()` 가 불가능한 전이 시 `IllegalStateException` 발생 + 종착 도달 시 `finishedAt` 기록.
- `isTerminal()` / `isInProgress()` 헬퍼 제공.

### 3.4 인증 (JWT) — `05_api_spec.md §1`

| 엔드포인트 | 상태 | 설명 |
|---|---|---|
| `POST /api/auth/signup` | 201 | 이메일 중복 시 `USER_EMAIL_DUPLICATED`(409) |
| `POST /api/auth/login` | 200 | 실패 시 `AUTH_INVALID_CREDENTIALS`(401), access+refresh 발급 |
| `POST /api/auth/refresh` | 200 | refresh 토큰 검증 후 재발급(만료/위조 구분) |
| `POST /api/auth/logout` | 204 | stateless(클라 토큰 폐기). 서버측 무효화는 2차 |

- Spring Security **stateless** + `JwtAuthenticationFilter`(Bearer 파싱 → `SecurityContext` 주입).
- access/refresh 를 `type` claim 으로 구분. 비밀번호 **BCrypt**. JWT 라이브러리 **jjwt 0.12**.
- 인증 실패(401)/권한 없음(403)은 EntryPoint/Handler 가 공통 에러 JSON 으로 응답.

### 3.5 Project CRUD — `05_api_spec.md §2`

| 엔드포인트 | 상태 | 설명 |
|---|---|---|
| `POST /api/projects` | 201 | subdomain 중복 시 409, webhookSecret **1회 평문 노출** + webhookUrl |
| `GET /api/projects` | 200 | 페이지네이션(`?page&size`), 본인 ACTIVE 프로젝트만 |
| `GET /api/projects/{id}` | 200 | webhookSecret **마스킹**(`whs_****`) |
| `PATCH /api/projects/{id}` | 200 | 부분 수정. `repositoryUrl`/`subdomain` 변경 금지 |
| `DELETE /api/projects/{id}` | 204 | **soft delete**(status=ARCHIVED) |

- 모든 접근에 **소유권 검증** → 타인 자원 접근 시 `PROJECT_ACCESS_DENIED`(403).
- subdomain 미지정 시 `project-{id}` 자동 생성.

### 3.6 공통(global) 인프라

- 응답 포맷: `ApiResponse<T>`(`{data}`), `PagedResponse<T>`(`{data, page}`).
- 에러 포맷: `{error:{code, message, details}}` + `ErrorCode`(명세 §0.4 전체) + `GlobalExceptionHandler`.
- validation 실패 → 400 `VALIDATION_ERROR` + 필드별 details.

---

## 4. 검증 결과

```
./gradlew clean build  →  BUILD SUCCESSFUL
- AutodeployApplicationTests.contextLoads  ✅ (전체 컨텍스트 로드, H2)
- DeploymentStatusTest                     ✅ (상태머신 전이/종착/진행 규칙)
```

- 테스트는 외부 의존(Docker/DB) 없이 통과 → CI 친화적.
- 로컬 실행(`bootRun`)은 `spring-boot-docker-compose` 가 `compose.yaml` 의 postgres 를 자동 기동/연결.

---

## 5. 환경 / 설정 메모

- **JDK 21 필요.** (`JAVA_HOME` = `C:\Program Files\Java\jdk-21.0.11`, 빌드 검증 완료)
- `application.properties`: `ddl-auto=update`, actuator `health/info/prometheus` 노출, JWT 설정(운영은 `JWT_SECRET` 환경변수 주입).
- 운영 JWT secret 은 반드시 환경변수로 주입(코드 기본값은 로컬 전용).

---

## 6. 다음 액션 (4주차로 이어질 항목)

1. **민준 인프라 의존**: RabbitMQ(`deploy.queue`)·Redis(배포 lock) 연동, 실제 postgres 기동 후 통합 테스트.
2. **Deployment/Env/Runtime/Webhook API**: 엔티티·repository 는 준비됨 → 컨트롤러/서비스만 추가.
3. **webhookSecret/환경변수 암호화** 적용(현재는 평문 저장 + TODO 표기).
4. (검토) 스키마 안정화 후 **Flyway** 전환.

---

## 7. 변경 파일 요약

```
backend/autodeploy/
  build.gradle                         (Java 전환 + validation/jjwt/h2/test 의존)
  src/main/resources/application.properties
  src/test/resources/application.properties  (신규: H2 테스트 설정)
  src/main/java/com/proj/autodeploy/
    AutodeployApplication.java
    global/{error,response,security,config}/   (11개: 응답·에러·JWT·SecurityConfig)
    user/        (User, UserStatus, UserRepository)
    auth/        (AuthController, AuthService, dto 5)
    project/     (Project 등 3 + Repository + Controller + Service + dto 5)
    environment/ (EnvironmentVariable, Repository)
    deployment/  (Deployment, DeploymentLog, enum 3, Repository 2)
    runtime/     (RuntimeInstance, enum 2, Repository)
    webhook/     (Webhook, Repository)
  src/test/java/.../deployment/domain/DeploymentStatusTest.java
```

---

## 8. 추가 작업 내역 (2026-06-08, 검증 · 보강)

> 위 구현(섹션 1~7) 이후 진행한 **로컬 DB 통합 검증 + 검토 보완** 작업.

### 8.1 로컬 Postgres 실연동 검증 (H2 아닌 실 DB)

- Docker로 `postgres:16` 기동(호스트 5433) → 앱이 실제 Postgres에 연결되어 정상 기동(`Started AutodeployApplication`).
- `ddl-auto=update` 로 **7개 테이블 자동 생성** 확인(`\dt`).
- 실 HTTP 엔드투엔드 스모크 테스트 전부 통과:

| 시나리오 | 결과 |
|---|---|
| 회원가입(201) → 로그인(200, access/refresh 발급) | ✅ |
| 프로젝트 생성(201, webhookSecret 평문 1회) | ✅ |
| 단건 조회(webhookSecret `whs_****` 마스킹) | ✅ |
| 목록(page 메타 포함) | ✅ |
| 잘못된 비밀번호 → 401 `AUTH_INVALID_CREDENTIALS` | ✅ |
| 토큰 없이 접근 → 401 | ✅ |
| 중복 이메일 → 409 `USER_EMAIL_DUPLICATED`(details 포함) | ✅ |

- DB 직접 쿼리로 users/projects row 저장 확인. 검증 후 임시 컨테이너/프로세스 정리 완료.
- 메모: 8080 포트가 점유 중이면 `SERVER_PORT`로 변경. PowerShell에서 한글이 `??`로 보이는 건 클라이언트 콘솔 인코딩 이슈(저장은 UTF-8 정상).

### 8.2 CORS 설정 추가 (프론트 연동 대비)

- `SecurityConfig`에 `CorsConfigurationSource` 빈 추가 + `http.cors(...)` 적용.
- 허용 origin: `http://localhost:5173`(Vite), `http://localhost:3000` / 메서드 GET·POST·PATCH·PUT·DELETE·OPTIONS / `/api/**`.
- → 민준의 프론트 dev 서버에서 API 호출 시 CORS 차단 없이 연동 가능.

### 8.3 API 통합 테스트 보강 (`ApiIntegrationTest`, H2)

- MockMvc 기반 6종: 회원가입·로그인 흐름 / 잘못된 비번 401 / 중복 이메일 409 / 프로젝트 생성·조회(마스킹)·목록 / 토큰 없이 401 / subdomain 자동생성.
- 외부 의존 없이(H2) CI에서 전체 스택 검증. **총 28개 테스트 그린.**
- 참고: Spring Boot 4에서 `@AutoConfigureMockMvc` 패키지가 `org.springframework.boot.webmvc.test.autoconfigure` 로 이동 → 임포트 수정.

### 8.4 🐛 버그 수정 — subdomain 자동 생성

- **증상**: subdomain 미지정으로 프로젝트 생성 시 INSERT 실패.
- **원인**: `@GeneratedValue(IDENTITY)`라 `save()` 시점에 즉시 INSERT 되는데, `subdomain`이 `NOT NULL`이라 null INSERT 불가.
- **수정**: 미지정 시 임시 placeholder(`pending-{uuid}`)로 INSERT → 발급된 id로 `project-{id}` 갱신.
- 이 케이스를 8.3 통합 테스트가 잡아냈음(테스트의 가치 입증).

---

## 9. 3주차 잔여 · 검토 사항 (4주차 이월 / 보완 권고)

| # | 항목 | 상태 | 비고 |
|---|---|---|---|
| 1 | webhookSecret / env value **암호화** | ⬜ TODO | 현재 평문 저장. AES-GCM 등 적용(가이드 §3.11) |
| 2 | **Swagger UI 동작 확인** | ⚠️ 미검증 | springdoc 3.0.2 ↔ Boot 4.0.6, 컨텍스트 로드는 정상. endpoint 스모크 체크 필요 |
| 3 | refresh 토큰 회전/blacklist | ⬜ 2차 | 현재 stateless, logout no-op |
| 4 | Env/Deployment/Runtime/Webhook **컨트롤러·서비스** | ⬜ 4주차 | 엔티티·repository는 준비됨 |
| 5 | Redis 분산락 / RabbitMQ `deploy.queue` | ⬜ 4주차 | 민준 인프라(`08_docker-compose.yml`) 완료 후 |
| 6 | **README** 로컬 실행법(backend/frontend/infra) | ⬜ TODO | 스프린트 DoD 항목 |
| 7 | `ddl-auto=update` → **Flyway + validate** 전환 | ⬜ 검토 | 스키마 안정화 후 |
| 8 | SecurityConfig 에러 JSON 수동 직렬화 | ⬜ 개선 | 메시지 escape 미흡(현재 고정 메시지라 위험 낮음) |

### ✅ 이번 보강으로 닫은 항목
- 프론트 연동 차단 요소(CORS) 해소
- 테스트 커버리지: 상태머신 단위 → **API 통합까지** 확장
- 실 Postgres 동작 + subdomain 자동생성 정합성 확인

---

## 10. 변경 이력 (이 문서)

| 일자 | 내용 |
|---|---|
| 2026-06-08 | 3주차 현수 과제 구현 보고(섹션 1~7) |
| 2026-06-08 | 추가: 로컬 Postgres 실연동 검증, CORS, API 통합 테스트 6종, subdomain 버그 수정, 잔여/검토 사항 정리(섹션 8~9) |
