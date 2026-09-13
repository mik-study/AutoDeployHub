# 4주차 과제 2번 — RabbitMQ 배선 구현 완료 브리핑

> 작성일: 2026-09-07
> 대상: 현수 4주차 과제 **2번 (RabbitMQ 연동 + Worker 컨슈머)**
> 선행 문서: `2026-06-16_week4_task2_rabbitmq_worker_consumer_현수.md` (개요),
> `2026-08-20_week4_task2_rabbitmq_구현_step_by_step.md` (실행 문서)
> 결과: **`./gradlew clean build` BUILD SUCCESSFUL / 테스트 44개 전부 통과 / AMQP 접속 오류 로그 0건**

---

## 1. 한 줄 요약

배포 요청이 **HTTP → DB 커밋 → RabbitMQ 발행 → 컨슈머 수신 → `QUEUED→CLONING` 전이 → Worker 진입점 호출**
까지 흐르는 배선을 완성했다. 파이프라인 본체(`DeploymentWorker.run`)는 과제 3 자리로 비워 두었다.

---

## 2. 완성된 흐름

```
[HTTP] POST /api/projects/{id}/deployments
   │
   ├─ DeploymentService.create()                     @Transactional
   │    ├─ Deployment 저장 (PENDING → QUEUED)
   │    └─ eventPublisher.publishEvent(DeploymentRequested)   ← 발행 예약만
   │
   ├─ ★ 트랜잭션 COMMIT ★                             ← 여기서 DB에 row 확정
   │
   ├─ DeploymentEventListener                        @TransactionalEventListener(AFTER_COMMIT)
   │    └─ RabbitDeploymentPublisher
   │         └─ rabbitTemplate.convertAndSend(deploy.exchange, deploy.request, msg)
   │
   └─ [HTTP 202 응답 반환]

┌─ 별도 스레드 (RabbitMQ 컨슈머) ─────────────────────────────┐
│ DeployQueueListener  @RabbitListener(queues="deploy.queue") │
│   ├─ lifecycleService.startProcessing(id)   @Transactional  │
│   │     · QUEUED 아니면 false → 조용히 종료 (멱등성)          │
│   │     · QUEUED 면 CLONING 전이 + markStarted()             │
│   └─ deploymentWorker.run(id)   ← 트랜잭션 밖. 과제 3 본체    │
└─────────────────────────────────────────────────────────────┘
```

### 큐 토폴로지

| 항목 | 값 |
|---|---|
| Exchange | `deploy.exchange` (direct, durable) |
| Queue | `deploy.queue` (durable) |
| Routing key | `deploy.request` |
| 메시지 포맷 | JSON (`JacksonJsonMessageConverter`) |
| 메시지 스키마 | `DeploymentRequested(deploymentId, projectId, branch, commitHash)` |

---

## 3. 변경 파일 목록

### 신규 (13개, 599줄)

**메인 소스 8개**

| 파일 | 역할 |
|---|---|
| `deployment/messaging/DeployQueueConstants.java` | 큐/익스체인지/라우팅키 상수 (발행·수신이 같은 문자열 참조) |
| `deployment/messaging/DeploymentRequested.java` | 메시지 계약 record. 내부 이벤트 payload 겸용 |
| `deployment/messaging/RabbitConfig.java` | Queue/Exchange/Binding + JSON MessageConverter 빈 |
| `deployment/messaging/RabbitDeploymentPublisher.java` | `DeploymentPublisher` 실구현 ("어떻게" 발행) |
| `deployment/messaging/DeploymentEventListener.java` | AFTER_COMMIT 훅 ("언제" 발행) |
| `deployment/messaging/DeployQueueListener.java` | `@RabbitListener` 컨슈머 |
| `deployment/DeploymentLifecycleService.java` | 워커 경로 상태 전이 전용 (짧은 트랜잭션) |
| `deployment/DeploymentWorker.java` | 과제 3 진입점 스텁 |

**테스트 5개**

| 파일 | 검증 내용 |
|---|---|
| `messaging/DeploymentRequestedTest.java` | JSON 라운드트립 3종 (값 보존 / content-type / null commitHash) |
| `messaging/RabbitDeploymentPublisherTest.java` | 약속된 exchange·routingKey 로 발행 |
| `messaging/DeploymentEventListenerTest.java` | **커밋 후에만 발행 / 롤백 시 미발행** (이번 과제 핵심 계약) |
| `messaging/DeployQueueListenerTest.java` | 워커 호출 / 멱등성 스킵 / 예외 삼키고 FAILED 기록 |
| `DeploymentLifecycleServiceTest.java` | QUEUED→CLONING, 멱등성, 없는 id, FAILED 전이, 상태머신 가드 |

### 수정 (6개)

| 파일 | 변경 |
|---|---|
| `build.gradle` | `spring-boot-starter-amqp` 추가 |
| `deployment/DeploymentPublisher.java` | 시그니처 `publish(Deployment)` → `publish(DeploymentRequested)` |
| `deployment/DeploymentService.java` | `DeploymentPublisher` 직접 호출 제거 → `ApplicationEventPublisher` 로 교체 |
| `src/main/resources/application.properties` | RabbitMQ 접속/리스너 설정 |
| `src/test/resources/application.properties` | 리스너 자동 기동 끄기 |
| `docker-compose.yml` | backend 에 `SPRING_RABBITMQ_*` env + `depends_on: rabbitmq(healthy)` |

### 삭제 (1개)

- `deployment/LoggingDeploymentPublisher.java` — 실구현만 남겨 빈 충돌 제거

---

## 4. step-by-step 문서와 달라진 점 (⚠️ 꼭 읽을 것)

문서를 그대로 따랐지만 **실제 환경에서 확인 후 4곳을 조정**했다. 이유가 전부 있다.

### 4-1. `DeployQueueListener` 에 `@ConditionalOnProperty` 추가 ★ 가장 중요

문서 Step 2-2는 테스트에서 `spring.rabbitmq.listener.simple.auto-startup=false` 만 넣으면
브로커 접속 재시도 로그가 사라진다고 했다. **절반만 맞다.**

- 테스트를 **패키지 단위로** 돌리면 깨끗하다.
- 그런데 **전체(`clean build`)로 돌리면 컨텍스트 하나에서 리스너가 뜨면서** `AmqpConnectException` 이 찍혔다.

원인은 Spring AMQP 쪽에 있다. `RabbitListenerEndpointRegistry.startIfNecessary()` 가 이렇게 생겼다
(spring-rabbit 4.0.3 바이트코드 확인):

```java
if (this.contextRefreshed || listenerContainer.isAutoStartup()) {
    listenerContainer.start();
}
```

즉 **`ContextRefreshedEvent` 가 이미 지나간 뒤 registry 가 다시 start 되면 `autoStartup=false` 를 무시하고
컨테이너를 띄운다.** 테스트 컨텍스트가 여러 개 캐시되는 상황에서 이게 터졌다.

그래서 **엔드포인트를 아예 등록하지 않는 쪽**으로 막았다.

```java
@Component
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true", matchIfMissing = true)
public class DeployQueueListener { ... }
```

- 운영: 이 프로퍼티가 없으므로 `matchIfMissing=true` 로 **항상 등록** → 동작 변화 없음
- 테스트: `auto-startup=false` 이므로 빈 자체가 안 만들어짐 → 접속 시도 0건

설정 하나(`auto-startup`)로 "리스너를 쓸 것인가"가 일관되게 묶인다.
적용 후 전체 빌드 로그에서 AMQP 오류가 **완전히 사라졌다.**

### 4-2. `application.properties` 의 한글 주석이 `?` 로 깨져 있었다 → 영어로 재작성

현재 unstaged 상태의 `src/main/resources/application.properties` 를 열어보니 이랬다.

```properties
# ????? ?? ?? ? ??. compose ? ?? ?? SPRING_RABBITMQ_HOST ? ???????.
```

`file` 로 확인하니 파일 전체가 **ASCII** 였다. 즉 편집기가 한글을 표현 못 하는 인코딩으로 저장하면서
**주석 내용이 실제로 소실**된 상태였다(화면 표시 문제가 아니라 디스크의 바이트가 `?`).

이 파일은 원래부터 주석이 전부 영어였으므로(`# --- DataSource (local PostgreSQL, no Docker) ---` 등),
**파일 관례에 맞춰 영어 주석으로 다시 썼다.** 내용은 문서와 동일하다.

> 💡 Java 소스(`*.java`)는 UTF-8 이라 한글 주석이 정상이다. `.properties` 파일만 이 문제가 있으니,
> 이 파일을 편집할 때는 인코딩(UTF-8)을 확인하거나 영어로 쓰는 편이 안전하다.

### 4-3. mockito 의존성 추가 — **불필요했다**

문서 Step 1은 mockito 가 클래스패스에 없을 수 있으니 확인 후 추가하라고 했다. 확인 결과:

```
org.mockito:mockito-core:5.20.0
org.mockito:mockito-junit-jupiter:5.20.0
```

`testCompileClasspath` / `testRuntimeClasspath` 양쪽에 **이미 들어와 있다**
(`spring-boot-starter-*-test` 가 전이 의존으로 끌어온다). **build.gradle 에 추가하지 않았다.**

### 4-4. JSON 컨버터 클래스명 — `JacksonJsonMessageConverter` 확정

문서 Step 5의 "확인 포인트"대로 `spring-amqp-4.0.3.jar` 안을 직접 열어보니 **둘 다 존재**한다.

- `Jackson2JsonMessageConverter` (Jackson 2 시절 이름, deprecated)
- `JacksonJsonMessageConverter` ← **이걸 사용**

의존성 트리에 `tools.jackson.core:jackson-databind:3.1.2` (Jackson 3)가 있어서 정상 동작한다.

### 4-5. (사소) `build.gradle` 의존성 위치 정리

unstaged 상태에서는 `testRuntimeOnly` 들 **아래에 4칸 스페이스**로 붙어 있었다.
파일이 탭 들여쓰기 + `implementation` 끼리 모아두는 관례라 **`starter-actuator` 바로 아래로 옮기고 탭으로 통일**했다.
기능 차이는 없다.

---

## 5. 기존 unstaged 변경사항 검토 결과

과제 2와 무관하지만 같이 커밋될 파일들이라 확인했다. **모두 그대로 두었다.**

| 변경 | 상태 | 메모 |
|---|---|---|
| `docker-compose.yml` postgres 호스트 포트 `5432→5433` | ✅ 유지 | 로컬 PostgreSQL 과 충돌 회피. 의도 명확 |
| backend `SPRING_DATASOURCE_URL` → `host.docker.internal:5432` | ✅ 유지 | 도커 postgres 대신 호스트 DB 사용. 이 구성에선 compose 의 `postgres` 서비스가 사실상 미사용 상태가 된다 |
| `VITE_API_BASE_URL` → `...test:8081/api` | ⚠️ 확인 필요 | 8081 은 traefik 이 바인드하는 포트인데, **현재 다른 프로젝트 컨테이너(`weekly-report-backend`)가 `0.0.0.0:8081` 을 점유 중**이다. compose 기동 전에 그쪽을 내리거나 포트를 바꿔야 한다 |
| `gradlew bootRun --project-cache-dir=...` | ✅ 유지 | 볼륨 마운트 환경에서 gradle 캐시 충돌 회피용으로 보임 |

---

## 6. 테스트 결과

```
./gradlew clean build   →   BUILD SUCCESSFUL
```

| 테스트 클래스 | 케이스 | 결과 |
|---|---:|---|
| `AutodeployApplicationTests` | 1 | ✅ |
| `ApiIntegrationTest` | 6 | ✅ |
| `DeploymentApiTest` | 2 | ✅ |
| `DeploymentStatusTest` (+중첩) | 21 | ✅ |
| `DeploymentLifecycleServiceTest` | 5 | 🆕 ✅ |
| `DeploymentRequestedTest` | 3 | 🆕 ✅ |
| `RabbitDeploymentPublisherTest` | 1 | 🆕 ✅ |
| `DeploymentEventListenerTest` | 2 | 🆕 ✅ |
| `DeployQueueListenerTest` | 3 | 🆕 ✅ |
| **합계** | **44** | **failures=0 / errors=0 / skipped=0** |

- 테스트 로그에 `AmqpConnectException` **0건** (§4-1 조치 후)
- 기존 테스트 4종은 손대지 않았고 그대로 통과한다.

### 특히 눈여겨볼 테스트 2개

**`DeploymentEventListenerTest`** — 이번 과제의 계약을 못 박는다.
`@Transactional` 을 일부러 안 붙였다. 붙이면 테스트 트랜잭션이 롤백돼서 AFTER_COMMIT 이 영원히 안 돌고,
지금 검증하려는 동작이 그대로 깨진다.

**`DeploymentLifecycleServiceTest.markFailedFromQueuedIsIgnored`** — "QUEUED 에서는 FAILED 로 못 간다"는
상태머신 제약을 테스트로 고정했다. 나중에 `QUEUED → FAILED` 를 추가하기로 하면 **이 테스트가 빨간불이 되면서
같이 고쳐야 할 곳을 알려준다.**

---

## 7. 수동 검증 절차 (아직 안 했음 — 직접 확인 필요)

컨테이너 스택이 안 떠 있어서 **실제 브로커 E2E 는 하지 않았다.** 아래 순서로 확인하면 된다.

```bash
docker compose up -d rabbitmq
```

1. **management UI** — http://localhost:15672 (`autodeploy_user` / `autodeploy_password`)
2. **백엔드 기동** 후 Exchanges 탭에 `deploy.exchange`, Queues 탭에 `deploy.queue` **자동 생성** 확인
   - 안 보이면 → 브로커 접속 실패. `spring.rabbitmq.host/username/password` 확인
3. **Queues → deploy.queue → Bindings** 에서 `deploy.exchange` ← `deploy.request` 바인딩 확인
4. **배포 요청** (프론트 [배포] 버튼 또는):
   ```bash
   curl -X POST http://localhost:8080/api/projects/1/deployments -H "Authorization: Bearer <토큰>" -H "Content-Type: application/json" -d "{}"
   ```
5. **로그가 이 순서로 찍히면 성공**
   ```
   published deploy request: deploymentId=1, ...      ← 발행 (커밋 후)
   received deploy request: deploymentId=1            ← 수신
   [TODO 과제 3] pipeline entry - deploymentId=1      ← 워커 진입
   ```
6. **DB** — `deployments` 해당 row 가 `CLONING` 이고 `started_at` 이 채워져 있으면 완료

### 메시지가 안 보일 때

| 증상 | 확인할 곳 |
|---|---|
| 큐/익스체인지가 UI에 아예 안 생김 | 브로커 접속 실패. compose env 와 properties |
| 발행 로그는 찍히는데 수신이 없음 | 라우팅 키 불일치가 1순위. UI Bindings 탭 ↔ `DeployQueueConstants` |
| 큐에 메시지가 쌓이기만 함 | 리스너 미기동. `auto-startup=false` 가 main properties 에 잘못 들어갔는지 확인 (§4-1 때문에 이 경우 리스너 빈 자체가 없다) |
| 발행 로그 자체가 없음 | 트랜잭션 미커밋 또는 `DeploymentEventListener` 가 빈으로 안 잡힘 |

---

## 8. 알려진 제약 (의도한 것)

1. **발행 실패 시 배포는 `QUEUED` 로 남는다.**
   브로커가 죽어 있으면 `DeploymentEventListener` 가 로그만 남긴다. 상태머신에서 `QUEUED` 는
   `CLONING`/`CANCELED` 로만 갈 수 있어 `FAILED` 로 정리할 수 없다. 사용자는 취소는 가능하다.
   → 과제 5에서 `QUEUED → FAILED` 를 추가할지 결정 (추가 시 `04_state_machine.md` 동반 수정)
2. **DLQ / 재시도 백오프 / 다중 큐 없음** — 4주차 "하지 말 것" 범위. 리스너가 예외를 삼켜
   무한 재큐잉을 막고, `default-requeue-rejected=false` 를 안전망으로 깔았다.
3. **중복 배포 가드는 아직 DB 기반** (`existsByProjectIdAndStatusIn`) — 과제 5에서 Redis 락으로 교체
4. **실제 브로커 통합 테스트 없음** — Testcontainers 는 4주차 범위 밖. §7 수동 검증으로 갈음

---

## 9. 과제 3·4·5로 넘기는 메모

| 과제 | 내용 |
|---|---|
| 3 | `DeploymentWorker.run(Long)` 시그니처 확정. **트랜잭션 밖 호출 / 예외 던져도 됨**이 계약 |
| 3 | 단계별 상태 전이 + `DeploymentLog` 적재 메서드는 `DeploymentLifecycleService` 에 추가 |
| 4 | SSE 는 `DeploymentLog` 적재 시점에 이벤트를 흘려야 하므로, 과제 3의 로그 적재 메서드에 훅 자리를 남길 것 |
| 5 | 발행 실패 시 `QUEUED` 고아 배포 처리 정책 (§8-1) |
| 5 | Redis 락 해제 훅 위치 — `DeployQueueListener` 의 종착 도달 지점 |

---

## 10. 완료 체크리스트

- [x] `spring-boot-starter-amqp` 추가, `./gradlew clean build` BUILD SUCCESSFUL
- [x] `LoggingDeploymentPublisher` 삭제, `DeploymentPublisher` 시그니처 변경
- [x] `DeploymentService` 가 `ApplicationEventPublisher` 로 이벤트만 발행
- [x] `deploy.exchange` / `deploy.queue` / 바인딩 선언 (`RabbitConfig`) — **UI 확인은 §7 미실시**
- [x] 컨슈머가 `QUEUED→CLONING` 전이 후 워커 진입점 호출, 멱등성 가드
- [x] 테스트 5종 신규 + 기존 4종 통과 (총 44 케이스)
- [x] 테스트 로그에 `AmqpConnectException` 없음
- [ ] **배포 요청 → 발행 → 수신 → `CLONING` 전이 E2E 눈으로 확인** ← §7 절차로 직접 확인 필요
