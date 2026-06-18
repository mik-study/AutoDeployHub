# 4주차 현수 과제 3번 — Worker 파이프라인 가이드

> 작성일: 2026-06-16
> 대상: 4주차 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **3번 (Worker 파이프라인)** 만
> 참조: `04_state_machine.md`, `03_erd.md`(runtime_instances), `05_api_spec.md §4`
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.

---

## 1. 과제 범위

과제 2 컨슈머가 호출하는 `deploymentWorker.run(deploymentId)`의 **본체**.
로컬 Docker에서 **단일 컨테이너(첫 배포 = BLUE)** 를 띄우는 것까지.

```
git clone → Dockerfile 검사 → docker build → docker run(env 주입 + traefik label)
→ health check → SUCCEEDED / FAILED
```

각 단계마다 `DeploymentLog` 적재 + `Deployment.transitionTo()` 로 상태 전이.

### ⚠️ 과제 3에서 제외 (경계선)

- **Blue-Green 트래픽 전환 / rollback** → 5주차. 1차는 BLUE 한 개만 띄우고 그게 곧 active.
- **Image Registry push** → 로컬 Docker라 registry 없음. 아래 §4.2 참고(상태는 통과시키되 no-op).

---

## 2. 상태머신 매핑 (★ 가장 중요)

`DeploymentStatus`는 **단계 건너뛰기를 허용하지 않는다.** `SUCCEEDED`는 반드시
`HEALTH_CHECKING → SWITCHING_TRAFFIC → SUCCEEDED` 경로로만 도달 가능하고,
`DEPLOYING`은 `PUSHING_IMAGE` 다음에서만 올 수 있다. 따라서 1차에서도 모든 상태를 거쳐야 한다.

| 파이프라인 단계 | 전이 | 4주차 처리 |
|---|---|---|
| (컨슈머가 진입) | `QUEUED → CLONING` | 과제 2에서 수행 |
| git clone | `CLONING` 유지 | 실제 clone |
| Dockerfile 검사 | `CLONING → CHECKING_DOCKERFILE` | 존재/유효성 확인 |
| 이미지 빌드 | `CHECKING_DOCKERFILE → BUILDING` | `docker build` |
| 이미지 푸시 | `BUILDING → PUSHING_IMAGE` | **로컬이라 no-op** (로그만, 상태는 통과) |
| 컨테이너 실행 | `PUSHING_IMAGE → DEPLOYING` | `docker run` (env + traefik label) |
| 헬스체크 | `DEPLOYING → HEALTH_CHECKING` | health path 폴링 |
| 트래픽 전환 | `HEALTH_CHECKING → SWITCHING_TRAFFIC` | **1차는 trivial** (첫 BLUE를 active로 표시만) |
| 완료 | `SWITCHING_TRAFFIC → SUCCEEDED` | RuntimeInstance active 확정 |
| 실패 | 각 단계 `→ FAILED` | `markFailureReason` + 로그 |

> `transitionTo()`가 위반 시 `IllegalStateException`을 던지므로, **순서를 임의로 건너뛰면 런타임 에러**가 난다. 단계 누락 금지.

---

## 3. Docker 제어 방식 결정 (공통 모임 안건)

| 방식 | 장점 | 단점 |
|---|---|---|
| **docker-java** (라이브러리) | 타입 안전, 빌드/로그 스트림 콜백, 테스트 용이 | 의존성·API 러닝커브 |
| **CLI 호출** (`ProcessBuilder`) | 단순, 동작 그대로, 로그 파싱 직관적 | 파싱 취약, OS 의존, 보안 |

> 권장: **docker-java** (빌드 로그를 콜백으로 받아 `DeploymentLog`에 그대로 적재하기 좋음). 단, 1차 데모 속도가 급하면 CLINFO 호출로 시작해도 무방 — §6 폴백 참조. **모임에서 확정**.

---

## 4. 구현 지침

### 4.1 Worker 서비스 구조
- `DeploymentWorker.run(Long deploymentId)` — `@Async` 또는 컨슈머 스레드에서 동기 실행(가상스레드 활용 가능).
- 각 단계를 메서드로 쪼개고, **단계 시작/종료마다 로그 적재 + 상태 전이**.
- 트랜잭션 경계 주의: 긴 작업이므로 단계별로 짧은 트랜잭션(상태 저장)으로 끊고, docker 작업 자체는 트랜잭션 밖에서.

### 4.2 단계별 핵심
- **clone**: `Project.repositoryUrl` + `defaultBranch`(또는 메시지 branch) → 임시 워크스페이스. `rootDirectory` 적용.
- **Dockerfile 검사**: `Project.buildType == DOCKERFILE` 기준 `rootDirectory`에 Dockerfile 존재/파싱.
- **build**: 이미지 태그 규칙 `autodeploy-runtime:project-{projectId}-deploy-{deploymentId}` → `Deployment.imageRepository/imageTag` 저장.
- **push**: 로컬 단일 호스트라 registry 없음 → **상태만 `PUSHING_IMAGE` 통과**시키고 로그 1줄. (추후 ECR 시 실제 push)
- **run**: `RuntimeColor.BLUE`(port 8081) → `docker run`에
  - **env 주입**: 과제 6 Env API의 복호화 값 → `-e KEY=VALUE` (과제 6 미완 시 빈 목록으로 진행).
  - **traefik label**: `project-{projectId}.autodeploy.test` 라우팅 (민준 인프라와 연결, label 키는 모임 합의).
- **health check**: `Project.healthCheckPath/Port/Timeout/Interval`로 폴링 → 성공 시 다음, 실패 시 `FAILED`.

### 4.3 RuntimeInstance 기록 (`03_erd.md`)
- `docker run` 직후 `RuntimeInstance` 생성: `color=BLUE`, `port=8081`, `status=STARTING`, `active=false`.
- health 성공 → `SWITCHING_TRAFFIC` 단계에서 `activate()` + `changeStatus(RUNNING)`.
- 실패 → `changeStatus(FAILED)`.

### 4.4 로그 적재 (`DeploymentLog`)
- 단계 메시지를 `DeploymentLog`로 저장. **`sequence`는 deployment별 1씩 증가** — `max(sequence)+1` 조회 또는 worker 스코프 카운터.
- 이 로그가 곧 과제 4 SSE/스냅샷의 데이터 소스. **적재 시점에 과제 4의 실시간 publish 훅도 호출**(자리만 남겨도 됨).

---

## 5. 연결축

- 과제 2: 컨슈머 → `deploymentWorker.run()` 진입.
- 과제 4: 각 `DeploymentLog` 적재 + 상태 전이 시 SSE로 push (`log`/`status` 이벤트), 종착 시 `close`.
- 과제 6: env 복호화 값 주입.
- 민준 인프라: docker socket 마운트(호스트 Docker 제어), traefik label 라우팅 PoC.

---

## 6. 폴백 (DoD 안전장치)

> `00_progress_history.md` 명시: **실제 `docker build`가 시간 과다면 빌드/실행을 mock 스텁으로 두고 상태머신 + SSE E2E 배선부터 완성** 후 실제 Docker로 교체.

- `DockerClient` 인터페이스로 추상화 → `MockDockerClient`(sleep + 가짜 로그)로 먼저 E2E 그린화 → 실제 구현 교체.

---

## 7. 테스트

- **단위**: 단계 순서대로 `transitionTo`가 호출되는지(mock docker), 실패 시 `FAILED` + `failureReason`.
- **통합**: MockDockerClient로 `run()` 전체 → 최종 `SUCCEEDED` + `RuntimeInstance` active + 로그 sequence 연속성.

## 8. 완료 체크리스트

- [ ] `run()`이 상태머신 전 경로(CLONING→…→SUCCEEDED)를 위반 없이 통과
- [ ] 각 단계 `DeploymentLog` 적재(sequence 연속) + 실패 시 FAILED/failureReason
- [ ] BLUE `RuntimeInstance` 생성·active, traefik label 부여
- [ ] env 주입 자리 + SSE push 훅 확보, MockDockerClient 폴백 가능
- [ ] `./gradlew clean build` BUILD SUCCESSFUL

## 9. 하지 말 것

- Blue-Green 전환·rollback (5주차) — 1차는 BLUE 단일
- Registry 실제 push / AWS 배포
- 단계 건너뛰기 (상태머신 위반 → 예외)
