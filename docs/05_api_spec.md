# 05. REST API 명세

> `01_domain_analysis.md` §9를 기반으로 구체화하고,
> `02_decisions.md`에서 결정한 MVP 범위(GitHub만, Blue-Green, 로컬 Docker, 모바일 제외)를 반영.
>
> - 작성자: 김현수
> - 작성일: 2026-06-01
> - 기본 prefix: `/api`
> - 인증: `Authorization: Bearer {accessToken}` (별도 명시 없으면 필수)
> - Content-Type: `application/json` (별도 명시 없으면)

---

## 0. 공통 규약

### 0.1 응답 포맷

**성공 (단건/리스트)**
```json
{
  "data": { ... }
}
```
```json
{
  "data": [ ... ],
  "page": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
}
```

**에러**
```json
{
  "error": {
    "code": "PROJECT_NOT_FOUND",
    "message": "Project not found: 12",
    "details": { "projectId": 12 }
  }
}
```

### 0.2 HTTP 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 성공 |
| 201 | 생성 성공 |
| 202 | 비동기 처리 시작(Deployment 요청 등) |
| 204 | 성공, 본문 없음 |
| 400 | 잘못된 요청 (validation) |
| 401 | 인증 실패 |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (이미 진행 중인 배포 등) |
| 422 | 비즈니스 규칙 위반 |
| 500 | 서버 에러 |

### 0.3 페이지네이션

쿼리 파라미터: `?page=0&size=20&sort=createdAt,DESC`

### 0.4 에러 코드 목록 (MVP)

```text
AUTH_INVALID_CREDENTIALS
AUTH_TOKEN_EXPIRED
AUTH_TOKEN_INVALID
USER_EMAIL_DUPLICATED
PROJECT_NOT_FOUND
PROJECT_ACCESS_DENIED
PROJECT_SUBDOMAIN_DUPLICATED
ENV_KEY_DUPLICATED
ENV_NOT_FOUND
DEPLOYMENT_NOT_FOUND
DEPLOYMENT_ALREADY_IN_PROGRESS  # 동일 프로젝트 동시 배포 차단
DEPLOYMENT_NOT_CANCELABLE       # 이미 종료/진행 단계 도달
DEPLOYMENT_NOT_ROLLBACKABLE     # previous_deployment 없음
WEBHOOK_SIGNATURE_INVALID
INTERNAL_ERROR
```

---

## 1. Auth

### 1.1 회원가입

```
POST /api/auth/signup
```

**Request**
```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd!",
  "name": "홍길동"
}
```

**Response 201**
```json
{
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동"
  }
}
```

**Error**: `USER_EMAIL_DUPLICATED` (409)

---

### 1.2 로그인

```
POST /api/auth/login
```

**Request**
```json
{ "email": "user@example.com", "password": "P@ssw0rd!" }
```

**Response 200**
```json
{
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "accessTokenExpiresIn": 1800
  }
}
```

**Error**: `AUTH_INVALID_CREDENTIALS` (401)

---

### 1.3 토큰 갱신

```
POST /api/auth/refresh
```

**Request**
```json
{ "refreshToken": "eyJhbGciOi..." }
```

**Response 200**: 1.2 와 동일

---

### 1.4 로그아웃

```
POST /api/auth/logout
```

**Response 204**

---

## 2. Project

### 2.1 프로젝트 생성

```
POST /api/projects
```

**Request**
```json
{
  "name": "filehub",
  "description": "파일 업로드 서비스",
  "repositoryUrl": "https://github.com/myorg/filehub",
  "defaultBranch": "main",
  "rootDirectory": "/",
  "healthCheckPath": "/health",
  "healthCheckPort": 8080,
  "healthCheckTimeoutSeconds": 60,
  "healthCheckIntervalSeconds": 5,
  "subdomain": "filehub"
}
```

**Response 201**
```json
{
  "data": {
    "projectId": 12,
    "name": "filehub",
    "subdomain": "filehub",
    "repositoryUrl": "https://github.com/myorg/filehub",
    "defaultBranch": "main",
    "status": "ACTIVE",
    "webhookUrl": "https://api.autodeploy.dev/api/webhooks/github",
    "webhookSecret": "whs_xxx...xxx",
    "createdAt": "2026-05-27T10:00:00Z"
  }
}
```

**Errors**:
- `PROJECT_SUBDOMAIN_DUPLICATED` (409)
- 400: validation (subdomain 형식, URL 형식 등)

**비고**
- `webhookSecret`은 생성 응답에서 **1회만** 평문 노출. 이후 조회 시 마스킹됨.
- GitHub repository의 Webhook 설정에 위 URL + secret 등록 필요.

---

### 2.2 프로젝트 목록

```
GET /api/projects?page=0&size=20
```

**Response 200**
```json
{
  "data": [
    {
      "projectId": 12,
      "name": "filehub",
      "subdomain": "filehub",
      "status": "ACTIVE",
      "lastDeployment": {
        "deploymentId": 101,
        "status": "SUCCEEDED",
        "finishedAt": "2026-05-26T09:42:11Z"
      }
    }
  ],
  "page": { "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

---

### 2.3 프로젝트 단건 조회

```
GET /api/projects/{projectId}
```

**Response 200**
```json
{
  "data": {
    "projectId": 12,
    "name": "filehub",
    "description": "파일 업로드 서비스",
    "repositoryUrl": "https://github.com/myorg/filehub",
    "defaultBranch": "main",
    "rootDirectory": "/",
    "buildType": "DOCKERFILE",
    "healthCheckPath": "/health",
    "healthCheckPort": 8080,
    "healthCheckTimeoutSeconds": 60,
    "healthCheckIntervalSeconds": 5,
    "subdomain": "filehub",
    "webhookSecret": "whs_****",
    "status": "ACTIVE",
    "createdAt": "2026-05-27T10:00:00Z"
  }
}
```

---

### 2.4 프로젝트 수정

```
PATCH /api/projects/{projectId}
```

**Request** (수정할 필드만)
```json
{
  "description": "신규 설명",
  "defaultBranch": "develop",
  "healthCheckPath": "/api/health"
}
```

**Response 200**: 2.3 과 동일 (수정 후 전체 응답)

**수정 불가 필드**: `repositoryUrl`, `subdomain` (MVP에서는 변경 금지)

---

### 2.5 프로젝트 삭제

```
DELETE /api/projects/{projectId}
```

**Response 204**

**동작**: soft delete (`status = ARCHIVED`). 실행 중인 컨테이너는 즉시 stop. 환경변수 / 배포 이력은 보관.

---

## 3. Environment Variable

### 3.1 환경변수 등록

```
POST /api/projects/{projectId}/env
```

**Request**
```json
{
  "key": "DATABASE_URL",
  "value": "postgres://...",
  "isSecret": true
}
```

**Response 201**
```json
{
  "data": {
    "envId": 31,
    "projectId": 12,
    "key": "DATABASE_URL",
    "isSecret": true,
    "createdAt": "2026-05-27T10:05:00Z"
  }
}
```

**Errors**: `ENV_KEY_DUPLICATED` (409)

---

### 3.2 환경변수 목록

```
GET /api/projects/{projectId}/env
```

**Response 200**
```json
{
  "data": [
    {
      "envId": 31,
      "key": "DATABASE_URL",
      "value": "****",
      "isSecret": true,
      "updatedAt": "2026-05-27T10:05:00Z"
    },
    {
      "envId": 32,
      "key": "APP_PORT",
      "value": "8080",
      "isSecret": false,
      "updatedAt": "2026-05-27T10:06:00Z"
    }
  ]
}
```

**비고**: `isSecret=true`인 항목은 `value=****` 마스킹.

---

### 3.3 환경변수 수정

```
PATCH /api/projects/{projectId}/env/{envId}
```

**Request**
```json
{ "value": "postgres://new..." }
```

**Response 200**: 3.1 형태

---

### 3.4 환경변수 삭제

```
DELETE /api/projects/{projectId}/env/{envId}
```

**Response 204**

---

## 4. Deployment

### 4.1 배포 요청

```
POST /api/projects/{projectId}/deployments
```

**Request** (commit 미지정 시 `defaultBranch`의 HEAD 사용)
```json
{
  "branch": "main",
  "commitHash": null
}
```

**Response 202** (비동기 시작)
```json
{
  "data": {
    "deploymentId": 101,
    "projectId": 12,
    "branch": "main",
    "commitHash": "a1b2c3d",
    "status": "QUEUED",
    "triggerType": "MANUAL",
    "createdAt": "2026-05-27T10:10:00Z"
  }
}
```

**Errors**:
- `DEPLOYMENT_ALREADY_IN_PROGRESS` (409)

**동작**: §04_state_machine.md `PENDING → QUEUED`. Redis lock 획득 후 RabbitMQ `deploy.queue` 발행.

---

### 4.2 배포 이력

```
GET /api/projects/{projectId}/deployments?page=0&size=20
```

**Response 200**
```json
{
  "data": [
    {
      "deploymentId": 101,
      "branch": "main",
      "commitHash": "a1b2c3d",
      "commitMessage": "feat: add upload",
      "status": "SUCCEEDED",
      "triggerType": "MANUAL",
      "startedAt": "2026-05-27T10:10:05Z",
      "finishedAt": "2026-05-27T10:12:34Z"
    }
  ],
  "page": { "page": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

---

### 4.3 배포 단건 조회

```
GET /api/deployments/{deploymentId}
```

**Response 200**
```json
{
  "data": {
    "deploymentId": 101,
    "projectId": 12,
    "previousDeploymentId": 100,
    "branch": "main",
    "commitHash": "a1b2c3d",
    "commitMessage": "feat: add upload",
    "imageRepository": "autodeploy-runtime",
    "imageTag": "project-12-deploy-101",
    "status": "SUCCEEDED",
    "triggerType": "MANUAL",
    "failureReason": null,
    "startedAt": "2026-05-27T10:10:05Z",
    "finishedAt": "2026-05-27T10:12:34Z",
    "createdAt": "2026-05-27T10:10:00Z"
  }
}
```

---

### 4.4 배포 취소

```
POST /api/deployments/{deploymentId}/cancel
```

**Response 200**
```json
{ "data": { "deploymentId": 101, "status": "CANCELED" } }
```

**Errors**: `DEPLOYMENT_NOT_CANCELABLE` (422)
- 가능: `PENDING` / `QUEUED` 상태만
- 불가: `BUILDING` 이후 — MVP에서는 진행 중 강제 중단 미지원

---

### 4.5 롤백

```
POST /api/deployments/{deploymentId}/rollback
```

**의미**: 지정한 deployment를 "Active 였던 상태"로 되돌림.
실제로는 그 deployment의 `previousDeploymentId`로 트래픽을 전환하는 **새로운 deployment** 를 생성한다.

**Request**: body 없음

**Response 202**
```json
{
  "data": {
    "deploymentId": 102,
    "projectId": 12,
    "branch": "main",
    "commitHash": "z9y8x7w",
    "status": "ROLLING_BACK",
    "triggerType": "ROLLBACK",
    "previousDeploymentId": 101,
    "createdAt": "2026-05-27T10:20:00Z"
  }
}
```

**Errors**:
- `DEPLOYMENT_NOT_ROLLBACKABLE` (422) — 이전 성공 배포가 없는 경우
- `DEPLOYMENT_ALREADY_IN_PROGRESS` (409)

---

### 4.6 배포 로그 조회 (스냅샷)

```
GET /api/deployments/{deploymentId}/logs?fromSequence=0&limit=200
```

**Response 200**
```json
{
  "data": [
    { "sequence": 1, "level": "INFO", "message": "Cloning repository...", "createdAt": "2026-05-27T10:10:05Z" },
    { "sequence": 2, "level": "INFO", "message": "Dockerfile validated", "createdAt": "2026-05-27T10:10:08Z" }
  ],
  "nextFromSequence": 3,
  "hasMore": true
}
```

---

### 4.7 배포 로그 실시간 스트림 (SSE)

```
GET /api/deployments/{deploymentId}/logs/stream
Accept: text/event-stream
```

**이벤트 스트림 예시**
```
event: log
data: {"sequence":3,"level":"INFO","message":"Building image..."}

event: log
data: {"sequence":4,"level":"INFO","message":"Step 1/8 FROM eclipse-temurin:21"}

event: status
data: {"status":"BUILDING"}

event: status
data: {"status":"SUCCEEDED"}

event: close
data: {}
```

**비고**:
- 배포가 종료 상태(`isTerminal`)에 도달하면 `close` 이벤트 후 서버에서 연결 종료
- 클라이언트 재연결 시 `Last-Event-ID` 헤더로 마지막 sequence 이후부터 전송

---

## 5. Runtime

현재 실행 중인 컨테이너(Blue/Green) 상태 조회.

### 5.1 런타임 인스턴스 목록

```
GET /api/projects/{projectId}/runtime
```

**Response 200**
```json
{
  "data": [
    {
      "runtimeInstanceId": 201,
      "deploymentId": 100,
      "containerName": "project-12-blue",
      "imageTag": "project-12-deploy-100",
      "color": "BLUE",
      "port": 8081,
      "isActive": false,
      "status": "RUNNING",
      "startedAt": "2026-05-26T09:40:00Z"
    },
    {
      "runtimeInstanceId": 202,
      "deploymentId": 101,
      "containerName": "project-12-green",
      "imageTag": "project-12-deploy-101",
      "color": "GREEN",
      "port": 8082,
      "isActive": true,
      "status": "RUNNING",
      "startedAt": "2026-05-27T10:12:00Z"
    }
  ]
}
```

**비고**: 화면에서 Blue-Green 현재 상태 시각화 시 사용.

---

### 5.2 런타임 종합 상태

```
GET /api/projects/{projectId}/runtime/status
```

**Response 200**
```json
{
  "data": {
    "projectId": 12,
    "subdomain": "filehub",
    "publicUrl": "https://filehub.autodeploy.dev",
    "activeColor": "GREEN",
    "activeDeploymentId": 101,
    "activeImageTag": "project-12-deploy-101",
    "standbyColor": "BLUE",
    "standbyDeploymentId": 100,
    "lastHealthCheckAt": "2026-05-27T10:13:00Z",
    "healthCheckStatus": "OK"
  }
}
```

---

### 5.3 런타임 로그 (컨테이너 stdout/stderr)

```
GET /api/projects/{projectId}/runtime/logs?tail=200&color=GREEN
```

**Response 200**
```json
{
  "data": {
    "color": "GREEN",
    "lines": [
      "2026-05-27T10:13:00Z INFO  Starting application...",
      "2026-05-27T10:13:01Z INFO  Tomcat started on port 8080"
    ]
  }
}
```

**비고**: `docker logs` 결과를 그대로 노출. MVP에서는 tail 기반 단순 조회만.

---

## 6. Webhook

### 6.1 GitHub Webhook 수신

```
POST /api/webhooks/github
X-GitHub-Event: push
X-GitHub-Delivery: 12345-uuid
X-Hub-Signature-256: sha256=...
Content-Type: application/json
```

**Request**: GitHub Webhook 원본 payload

**Response 202**
```json
{
  "data": {
    "webhookId": 555,
    "deploymentId": 103,
    "projectId": 12
  }
}
```

**동작**:
1. `X-Hub-Signature-256` 검증 (실패 시 `WEBHOOK_SIGNATURE_INVALID` 401)
2. `X-GitHub-Delivery` 중복 체크 (이미 처리한 delivery는 200 + `processed:true` 만 반환)
3. payload의 ref가 프로젝트 `defaultBranch`와 일치하지 않으면 200 무시
4. Deployment 생성 + queue 발행 (4.1과 동일 로직)

**비고**:
- 이 endpoint는 **인증 헤더 없이** 호출됨. 인증은 signature 검증으로 대체.
- 처리 결과는 비동기. 즉시 Deployment ID 반환.

---

## 7. (MVP 제외) — 명시적으로 만들지 않는 것

- ❌ GitLab 관련 endpoint
- ❌ OAuth 로그인 (GitHub / Google 등)
- ❌ Private repository GitHub Token 등록 endpoint
- ❌ ECR / AWS credentials 관련 endpoint
- ❌ 팀/멤버십 endpoint
- ❌ 모바일 앱 전용 endpoint (FCM 등록 등)
- ❌ 알림 채널 설정 (Slack / Email)

---

## 8. 권한 매트릭스 (MVP)

| 행위 | 본인 | 타인 |
|---|---|---|
| 자신의 프로젝트 조회/수정/배포 | ✅ | ❌ (403 `PROJECT_ACCESS_DENIED`) |
| 자신의 배포 로그 조회 | ✅ | ❌ |
| Webhook 수신 | (인증 불필요, signature 검증) | — |

MVP는 단일 소유자(owner) 모델. 팀/공유는 2차.

---

## 9. 다음 단계

- `06_sequence_diagrams.md`(B 담당) 의 흐름이 본 명세의 endpoint 호출과 1:1로 대응되어야 함.
- `08_docker-compose.yml`(B 담당) 의 traefik 라우팅 설정이 본 명세의 `subdomain` / `publicUrl` 규칙과 정합되어야 함.
