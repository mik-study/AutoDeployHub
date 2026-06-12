# 2026-06-12 3주차 프론트엔드·인프라 진행 보고서 (민준)

## 1. 개요

`docs/00_progress_history.md`의 **"민준 담당 — 인프라 & 프론트엔드 skeleton"** 항목을 기준으로,
이번 주 진행한 프론트엔드/로컬 인프라 작업을 정리한다.

범위는 다음 4가지다.

1. 로컬 인프라 compose 정리
2. 프론트 기반 구조 구성
3. 로그인 / 회원가입 / 프로젝트 목록 화면 구현 및 API 연동
4. Traefik 기반 로컬 서브도메인 라우팅 검증

---

## 2. 항목별 진행 결과

### 2.1 `08_docker-compose.yml` 로컬 인프라 구성

`docs/08_docker_compose/docker-compose.yml` 기준으로 다음 서비스를 한 번에 기동하는 구성을 정리했다.

- `postgres`
- `redis`
- `rabbitmq` (+ management UI)
- `traefik`
- `frontend`
- `backend`

주요 정리 사항:

- `backend/autodeploy/compose.yaml`은 **백엔드 단독 로컬 보조용**에 가깝고,
  `docs/08_docker_compose/docker-compose.yml`은 **프론트/백엔드/인프라를 함께 올리는 통합 실행용**으로 역할을 분리했다.
- 네트워크는 `autodeploy-network`로 통일했다.
- DB/Redis/RabbitMQ/Gradle/npm 의존성 캐시를 위해 volume을 정의했다.
- Traefik이 `Host` 기반으로 프론트/백엔드 라우팅을 처리하도록 label을 설정했다.

실행 중 확인된 이슈:

- Windows `PID 4`가 `127.0.0.1:80`을 점유하고 있어 Traefik 기본 포트 `80` 바인딩이 실패했다.
- 이를 피하기 위해 로컬 테스트 포트를 **`8081`** 로 조정했다.

현재 기준 접속 가능:

- 프론트: `http://dashboard.autodeploy.test:8081`
- 백엔드 API: `http://api.autodeploy.test:8081/api`
- Traefik Dashboard: `http://localhost:8080`

---

### 2.2 프론트 기반 구조 구성

`frontend/` 기준으로 다음 기반 구성을 완료했다.

- `vue-router`
- `pinia`
- `axios` 기반 API client
- JWT access/refresh token 저장 유틸
- 인증 가드 기반 라우팅
- 인증 화면 레이아웃 / 앱 레이아웃 분리

적용 내용:

- 인증이 필요한 라우트 접근 시 로그인 화면으로 redirect
- 로그인/회원가입 화면에서는 auth layout 사용
- 앱 내부 화면에서는 사이드바가 있는 공통 레이아웃 사용
- API 요청 시 access token 자동 첨부
- `401` 발생 시 refresh token으로 `/auth/refresh` 1회 재시도
- 로그아웃 시 토큰 및 사용자 표시 정보 제거

---

### 2.3 화면 구현 및 API 연동

#### 로그인 / 회원가입

구현 완료:

- 로그인 화면 UI
- 회원가입 화면 UI
- `/api/auth/login` 연동
- `/api/auth/signup` 연동
- `/api/auth/refresh` 연동
- `/api/auth/logout` 연동

보완 사항:

- 개발 환경에서 백엔드 없이도 확인할 수 있도록 dev 로그인 fallback 추가
- 회원가입 화면에 비밀번호 길이 검증(`8~64자`) 추가
- 로그인 사용자 정보는 현재 백엔드 응답 구조에 맞춰 fallback 처리
  - 백엔드가 `user` 정보를 내려주지 않으면 이메일 앞부분을 이름으로 사용

#### 프로젝트 목록

구현 완료:

- 프로젝트 목록 화면 UI
- 검색 입력창
- 페이지네이션
- `/api/projects` 연동
- mock 데이터 기반 목록 표시 보조 로직

정리 사항:

- 프론트 `ProjectStatus`는 백엔드 기준으로 `ACTIVE | ARCHIVED`에 맞췄다.
- 화면에 표시하는 `RUNNING / PENDING / FAILED`는 `lastDeployment.status` 기준의
  **화면용 상태 계산 로직**으로 분리했다.
- 프로젝트명(`name`)과 서브도메인(`subdomain`)은 역할이 다르도록 mock 데이터도 구분했다.

#### 기타 메뉴 화면

사이드바 메뉴 테스트와 이후 확장을 위해 placeholder view를 추가했다.

- `IntegrationChannelsView`
- `NotificationSettingsView`
- `UserManagementView`
- `UserSettingsView`

각 메뉴는 실제 라우트로 이동하며, 현재는 메뉴 이름이 구분되게만 표시한다.

#### 프로젝트 생성 (`POST /api/projects`)

구현 완료:

- `[새 프로젝트]` 버튼에서 `/projects/new` 라우트로 이동
- 프로젝트 생성 폼 UI
- `health check` 항목을 고급 설정으로 접는 UI 적용
- `POST /api/projects` 연동
- 백엔드 미연결 개발 환경용 mock 생성 fallback
- 생성 성공 후 `webhookUrl`, `webhookSecret` 표시 패널

메모:

- 백엔드의 `CreateProjectRequest`, `CreateProjectResponse`, 검증 규칙, `subdomain` 자동 생성 로직 기준으로 프론트 폼과 payload를 맞췄다.
- 생성 성공 시 `webhookSecret`은 1회만 평문 노출되는 특성을 고려해,
  목록으로 즉시 이동하지 않고 Webhook 안내 패널을 먼저 표시한다.
- GitHub 저장소 URL 형식, `subdomain` 형식은 프론트에서도 사전 검증한다.

---

## 3. 백엔드 연동 기준 점검

`58b5c20 ([BE] feat: 백엔드 기초 구현)` 기준으로 프론트와 대조한 결과,
핵심 계약은 다음과 같이 확인했다.

일치하는 부분:

- `/api/auth/signup`
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/auth/logout`
- `/api/projects`
- 목록 응답 구조: `{ data, page }`

주의가 필요한 부분:

- 로그인 응답은 현재 **토큰만 반환**하며, 사용자 이름/이메일을 내려주지 않는다.
- 프로젝트 목록 응답의 `lastDeployment`는 아직 백엔드에서 본격 구현 전 단계다.
- `activeVersion`, `BLUE/GREEN` 표시는 현재 프론트 mock/UI 보조 정보다.

---

## 4. 이번 주 산출물 요약

- 로컬 통합 compose 초안 정리 및 실행 이슈 확인
- Traefik 기반 프론트/백엔드 서브도메인 라우팅 설정
- Vue Router / Pinia / Axios 기반 프론트 skeleton 구성
- 로그인 / 회원가입 / 프로젝트 목록 / 프로젝트 생성 화면 구현
- JWT 토큰 기반 인증 흐름 및 refresh 재시도 로직 구성
- 사이드바 placeholder 화면 추가

---

## 5. 다음 작업

우선순위는 다음과 같다.

1. 프로젝트 상세 화면 추가
2. 백엔드 로그인 응답에 사용자 정보(`name`, `email`) 포함 여부 협의
3. 프로젝트 목록의 배포/활성 버전 정보가 실제 API에서 내려오도록 백엔드 확장 여부 확인
4. README에 로컬 실행 방법(프론트/백엔드/compose) 정리
