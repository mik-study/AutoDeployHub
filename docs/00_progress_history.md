# 00. 진행 내역 (Progress History)

> AutoDeployHub 팀 프로젝트의 주차별 진행 상황을 추적하는 마스터 문서.
> 새 주차가 끝나면 이 문서를 갱신한다. 상세 산출물은 각 번호 문서(01~08) 참조.
>
> - 팀: 2인 (현수, 민준)
> - 저장소: `C:\khs\AutoDeployHub`
> - 최종 갱신: 2026-06-03

---

## 0. 한눈에 보기

| 주차 | 기간 | 테마 | 상태 |
|---|---|---|---|
| 1주차 | ~2026-05-26 | 도메인 분석 + 아키텍처 다이어그램 | ✅ 완료 |
| 2주차 | 2026-05-27 ~ 2026-06-02 | MVP 설계 통일 + 설계 산출물 + 프로젝트 init | ✅ 완료 |
| 3주차 | 2026-06-03 ~ | **코드 시작** — 인프라 기동 + 엔티티/인증 + 프론트 skeleton | 🚧 진행 예정 |

### 산출물 인덱스

| 문서 | 내용 | 담당 | 상태 |
|---|---|---|---|
| `01_domain_analysis.md` | 개발 가이드 / 도메인 지식 / 테스트 시나리오 | 공통 | ✅ |
| `02_decisions.md` | 설계 충돌 정리 + MVP 8개 의사결정 | 현수 | ✅ |
| `03_erd.md` | 7개 엔티티 ERD (Blue-Green 반영) | 현수 | ✅ |
| `04_state_machine.md` | Deployment 상태머신 (15 states) | 현수 | ✅ |
| `05_api_spec.md` | REST API 명세 초안 | 현수 | ✅ |
| `06_sequence_diagrams/` | 시퀀스 다이어그램 4종 (PNG) | 민준 | ✅ |
| `07_wireframes/` | 화면 와이어프레임 (PNG) | 민준 | ✅ |
| `08_docker-compose.yml` | 로컬 인프라 compose 초안 | 민준 | 🚧 3주차 (현재 backend/autodeploy/compose.yaml에 postgres만) |

---

## 1. 1주차 — 도메인 분석 & 아키텍처 (✅ 완료)

### 성과
- **도메인 확정**: 배포 자동화 플랫폼 (Web)
- **테스트 대상**: File Hub
- **개발 가이드(.md) 작성** → `01_domain_analysis.md`
  - CI/CD, GitHub 연동, Docker, Registry, Reverse Proxy, 상태머신, Health Check, Rollback, 로그, Queue/Worker, 보안 등 핵심 도메인 지식 정리
  - 도메인 모델 7종 초안 (User, Project, EnvironmentVariable, Deployment, DeploymentLog, RuntimeInstance, Webhook)
  - REST API 설계 초안, Worker 처리 흐름, 테스트 계층/시나리오
- **Notion 아키텍처 다이어그램 5장** 작성 (시각 자료)

### 초기 기술스택 합의 (Notion)
- 프론트: Vue 3 + TypeScript + Vite
- 백엔드: Spring Boot + JPA *(2주차에 Java 21 + Spring Boot 4.0.6으로 구체화됨)*
- DB: PostgreSQL / Redis
- MQ: RabbitMQ
- 인프라: Docker + Reverse Proxy
- 모니터링: Prometheus + Grafana / ELK (추후 결정)

---

## 2. 2주차 — MVP 설계 통일 & 프로젝트 init (✅ 완료)

> 핵심: 1주차의 가이드(.md)와 Notion 다이어그램이 **8개 항목에서 충돌**했던 것을 회의로 통일하고,
> 통일된 설계 위에 ERD / 상태머신 / API / 시퀀스 / 와이어프레임을 작성. 마지막으로 코드 베이스 init.

### Step 1. 설계 충돌 통일 (✅ 2026-05-27 회의)
8개 항목 의사결정 완료 → `02_decisions.md`

| 항목 | 최종 결정 |
|---|---|
| Reverse Proxy | **Traefik** |
| Image Registry | **로컬 Docker** (추후 ECR) |
| Runtime 환경 | **로컬 Docker** (추후 AWS EC2) |
| 실패 대응 | **Blue-Green 무중단** (자동 Rollback 대신) |
| 모니터링 | **Prometheus + ELK** (1차는 Prometheus만) |
| Source Repo | **GitHub만** |
| Queue 구조 | **deploy.queue 1개** (추후 확장) |
| 모바일 앱 | **MVP 제외** (Flutter 추후) |

### Step 2. 설계 산출물 (✅ 완료)

**👤 현수 — 도메인/백엔드 설계**
- ✅ `03_erd.md` — 7개 엔티티 ERD. `runtime_instances`에 `color`/`port`/`is_active` 컬럼으로 Blue-Green 반영, 인덱스/제약 명세
- ✅ `04_state_machine.md` — Deployment 상태머신 15개 상태 + `SWITCHING_TRAFFIC` 추가, 실패/롤백 흐름, enum 정의, Lock 정책
- ✅ `05_api_spec.md` — REST API 명세 초안 (Auth/Project/Env/Deployment/Runtime/Webhook)

**👤 민준 — 흐름/프론트/운영 설계**
- ✅ `06_sequence_diagrams/` — 시퀀스 다이어그램 4종
  - (a) 수동 배포 정상 (Blue → Green 전환)
  - (b) 빌드 실패 → FAILED → 알림
  - (c) Health Check 실패 → Blue 트래픽 유지 (즉시 롤백)
  - (d) Webhook 자동 배포 (Signature 검증)
- ✅ `07_wireframes/` — 화면 와이어프레임
  - 로그인 / 프로젝트 목록
  - 프로젝트 상세 (개요·배포 이력·로그·환경 변수·모니터링·Webhook)
  - 관리 (사용자 관리·알림 설정·통합 채널)

### Step 3. 프로젝트 Init (✅ 완료)
- ✅ GitHub repo 생성 (민준)
- ✅ 백엔드 프로젝트 생성 (현수) — `backend/autodeploy/`
  - **Java 21** + Spring Boot **4.0.6** + Gradle (Java 21 toolchain, Virtual Thread 활용 방향)
  - 의존성: data-jpa, security, oauth2-client, webmvc, actuator, **micrometer-prometheus**, springdoc-openapi, postgresql, lombok
  - `compose.yaml` (postgres) + `spring-boot-docker-compose` 연동
  - ⚠️ 로컬 빌드 시 **JDK 21 필요** (현재 머신엔 JDK 17만 설치됨 → 21 설치 후 `./gradlew build`)
- ✅ 프론트엔드 프로젝트 생성 (현수) — `frontend/`
  - Vue 3.5 + TypeScript + Vite 8 (현재는 기본 스캐폴드 / HelloWorld)

### ⚠️ 2주차 진행 중 발생한 변경점 (설계 대비)
다음은 init 과정에서 원래 계획과 달라진 부분 — 3주차에 반영/정리 필요:

1. **모듈 구조: 멀티모듈 → 현재 단일 모듈**
   - 가이드 §7은 `autodeploy-common / -api / -worker` 멀티모듈 제안.
   - 실제 init은 단일 모듈(`com.proj.autodeploy`). → 3주차에 멀티모듈 전환 여부 결정.
2. **compose 범위: postgres만 존재**
   - `02_decisions.md` TODO의 `08_docker-compose.yml`(postgres+redis+rabbitmq+traefik)은 아직 미완.
   - → 3주차 인프라 기동 과제로 이관.

> 참고: 초기 스캐폴드가 Kotlin으로 생성됐었으나 2026-06-03 **Java 21로 전환** 완료 (계획대로 Java 확정).

---

## 3. 3주차 — 첫 코딩 스프린트 (🚧 진행 예정)

> 목표: **"확정된 설계 위에 실제로 도는 뼈대"** 만들기.
> 끝나면 `docker compose up` → 백엔드 기동 → 프론트에서 로그인/프로젝트 목록 호출까지 한 줄로 이어져야 함.

### 스프린트 목표 (Definition of Done)
- [ ] `docker compose up` 으로 postgres + redis + rabbitmq + traefik 한 번에 기동
- [ ] 백엔드: ERD 7개 엔티티 + enum이 JPA로 매핑되고 스키마 자동 생성/마이그레이션
- [ ] 백엔드: 회원가입/로그인(JWT) + 프로젝트 CRUD API 동작 (Swagger에서 확인)
- [ ] 프론트: 라우터 + 레이아웃 + 로그인 화면 + 프로젝트 목록 화면이 실제 API와 연동
- [ ] README에 로컬 실행 방법(backend/frontend/infra) 정리

### 👤 현수 담당 — 백엔드 도메인 & 인증

1. **모듈 구조 결정 & 정리** (먼저)
   - 멀티모듈(`-common/-api/-worker`) 전환 vs 단일 모듈 유지를 회의에서 확정
   - 결정 결과를 `02_decisions.md` 변경 이력에 추가
2. **JPA 엔티티 + enum 구현** (`03_erd.md` 그대로)
   - `User`, `Project`, `EnvironmentVariable`, `Deployment`, `DeploymentLog`, `RuntimeInstance`, `Webhook`
   - `DeploymentStatus`(15) / `RuntimeColor`(BLUE·GREEN) / `DeploymentTriggerType` enum (`04_state_machine.md` §6)
   - 스키마 관리: Flyway 또는 `ddl-auto=validate` 전략 결정 후 적용
   - 인덱스/유니크 제약을 `03_erd.md` §3 SQL 기준으로 반영
3. **인증 (JWT)**
   - `POST /api/auth/signup`, `/login`, `/refresh`, `/logout`
   - Spring Security + JWT 필터, 비밀번호 BCrypt
4. **Project CRUD API**
   - `POST/GET/GET{id}/PATCH/DELETE /api/projects`
   - `05_api_spec.md`의 request/response 포맷·에러 포맷·페이지네이션 규칙 준수
   - `subdomain` 자동 생성 로직 (`project-{id}`)
5. **DeploymentStatus 상태머신 단위 테스트** (`04_state_machine.md` §7)
   - 정상 전이 / FAILED 진입 / terminal 상태 전이 거부 / `isTerminal()`·`isInProgress()`

### 👤 민준 담당 — 인프라 & 프론트엔드 skeleton

1. **`08_docker-compose.yml` 완성** (로컬 인프라)
   - `postgres`, `redis`, `rabbitmq`(management UI 포함), `traefik`
   - 기존 `backend/autodeploy/compose.yaml`과의 관계 정리 (통합 or 분리 결정)
   - 포트/볼륨/네트워크 정의, `docker compose up` 실제 기동 검증
2. **프론트 기반 구성**
   - `vue-router` + 레이아웃(사이드바/헤더) 셋업
   - `axios`(또는 fetch wrapper) + API 베이스 URL/인터셉터(JWT 첨부)
   - Pinia 등 상태관리 도입 여부 결정
3. **화면 구현 (와이어프레임 → 실제 페이지)**
   - 로그인 화면 → `/api/auth/login` 연동 (토큰 저장)
   - 프로젝트 목록 화면 → `/api/projects` 연동
   - (여유 시) 프로젝트 생성 폼 → `POST /api/projects`
4. **Traefik 라우팅 PoC**
   - `dashboard.autodeploy.dev`(프론트), `api.autodeploy.dev`(백엔드) 로컬 hosts 매핑으로 서브도메인 라우팅 확인

### 공통 / 모임
- 모임 시작: 모듈 구조 + Flyway 여부 + compose 통합 방식 결정 (30분)
- 모임 끝: ERD ↔ 엔티티 ↔ API 정합성 재확인, 4주차 과제(Worker 골격/배포 파이프라인) 결정

### ⚠️ 3주차에 하지 말 것
- AWS 리소스 실제 생성 (로컬로 충분)
- 실제 Docker build/배포 파이프라인 구현 (그건 4주차 — 이번 주는 뼈대까지)
- ELK / 다중 Queue / Flutter — MVP 제외 그대로 유지

---

## 4. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-06-03 | 진행 내역 문서 최초 작성. 1·2주차 완료 기록, 3주차 과제 정의 |
