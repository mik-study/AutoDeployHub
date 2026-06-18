# 4주차 현수 과제 2번 — RabbitMQ 연동 + Worker 컨슈머 가이드

> 작성일: 2026-06-16
> 대상: 4주차 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **2번 (RabbitMQ 연동 + Worker 컨슈머)** 만
> 참조: `04_state_machine.md`, `05_api_spec.md §4`, 과제 1번 가이드의 `DeploymentPublisher` seam
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.

---

## 1. 과제 범위

배포 요청을 **비동기 큐로 흘려보내고, 컨슈머가 받아 상태 전이를 시작**하는 배선까지.
실제 git clone/docker build 등 파이프라인 본체는 **과제 3**에서 채운다.

| 항목 | 내용 |
|---|---|
| 큐 인프라 | `deploy.queue` + exchange + routing key 선언 (Spring AMQP) |
| 발행 | 과제 1의 `DeploymentPublisher` 스텁을 **RabbitMQ 실제 발행 구현체로 교체** |
| 메시지 스키마 | `DeploymentRequested { deploymentId, projectId, branch, commitHash }` (공통 모임에서 합의) |
| 컨슈머 | `@RabbitListener`로 수신 → deployment 로드 → `QUEUED→CLONING` 전이 후 **Worker 파이프라인(과제 3) 진입점 호출** |

### ⚠️ 과제 2에서 제외 (경계선)

- **파이프라인 본체**(clone/build/run/health) → 과제 3. 여기선 컨슈머가 Worker 서비스의 **빈 진입 메서드만 호출**.
- **DLQ / 재시도 정책 / 다중 큐** → "4주차에 하지 말 것". 1차는 `deploy.queue` 1개.

---

## 2. 사전 의존성

- 🔧 **`spring-boot-starter-amqp` 의존성 추가 필요** (현재 `build.gradle`에 없음).
- ✅ RabbitMQ 인프라는 민준이 3주차 `08_docker_compose`에 management UI 포함해 기동(연결 검증은 민준 과제 5와 함께).
- ✅ 과제 1에서 `DeploymentPublisher` 인터페이스 + `LoggingDeploymentPublisher` 스텁이 이미 박혀 있음 → **이 스텁을 교체**한다.

`application.properties`에 추가할 항목(예시 키):
```
spring.rabbitmq.host / port / username / password   # compose 기준
# 큐/익스체인지 이름은 상수 또는 @ConfigurationProperties 로 한 곳에서 관리
```

---

## 3. 새로 만들 파일

```
deployment/
 ├ messaging/
 │   ├ RabbitConfig.java              # Queue/Exchange/Binding Bean + Jackson2JsonMessageConverter
 │   ├ DeploymentRequested.java       # record 메시지 DTO (공통 합의 스키마)
 │   ├ RabbitDeploymentPublisher.java # DeploymentPublisher 구현 (RabbitTemplate)
 │   └ DeployQueueListener.java       # @RabbitListener 컨슈머
 └ (LoggingDeploymentPublisher 는 @Profile 로 분리하거나 삭제)
```

> `DeploymentPublisher` 빈이 둘(스텁/실구현)이 되지 않도록 `@Profile`/`@Primary`로 정리하거나 스텁 제거.

---

## 4. 구현 지침

### 4.1 큐/익스체인지 선언 (`RabbitConfig`)
- `deploy.queue`(durable) + direct exchange + routing key 1개 바인딩.
- **`Jackson2JsonMessageConverter` 빈 등록** → 메시지를 JSON으로 직렬화(가독성·디버깅 용이, management UI에서 확인).
- 큐/키 문자열은 상수 클래스 한 곳에 모아 발행/수신이 같은 값을 참조하게 한다.

### 4.2 메시지 스키마 (`DeploymentRequested`)
- `record DeploymentRequested(Long deploymentId, Long projectId, String branch, String commitHash)`.
- **엔티티를 그대로 직렬화하지 말 것** — 전용 record로 계약 고정(공통 모임 합의 포맷).

### 4.3 발행 (`RabbitDeploymentPublisher`)
- 과제 1 `DeploymentService`가 호출하는 `publish(Deployment)` 시그니처 유지 → 내부에서 `DeploymentRequested`로 변환 후 `RabbitTemplate.convertAndSend`.
- **트랜잭션 커밋 이후 발행**이 정석: `@TransactionalEventListener(phase = AFTER_COMMIT)` 또는 `TransactionSynchronizationManager` 사용 권장.
  - 이유: 커밋 전에 발행하면 컨슈머가 **아직 DB에 없는 deployment**를 조회해 실패할 수 있음(레이스).
  - 과제 1의 스텁 주석("커밋 후 발행으로 다듬기")을 여기서 실제로 반영.

### 4.4 컨슈머 (`DeployQueueListener`)
- `@RabbitListener(queues = DEPLOY_QUEUE)` 메서드가 `DeploymentRequested` 수신.
- 처리: deployment 로드 → 상태가 `QUEUED`인지 확인(아니면 무시/로그) → `transitionTo(CLONING)` + `markStarted()` → **`deploymentWorker.run(deploymentId)`(과제 3 진입점) 호출**.
- **멱등성**: 같은 메시지 재수신 대비, 이미 진행/종착 상태면 재처리하지 않도록 가드.
- **에러 처리(1차)**: 예외 시 deployment를 `FAILED`로 두고 `markFailureReason`. DLQ/재시도는 미도입이므로 **재큐잉 루프 방지**를 위해 리스너에서 예외를 삼키고 상태만 FAILED 처리(또는 `AcknowledgeMode` 정리).

---

## 5. 과제 3·5와의 연결

- 과제 3: 컨슈머가 호출하는 `deploymentWorker.run(deploymentId)`가 파이프라인 본체. 과제 2에선 **메서드 시그니처만 확정**하고 호출.
- 과제 5: 발행 직전 Redis 락 획득은 과제 1 가드를 교체하는 형태 → 발행 시점/락 해제 시점(컨슈머 종착)에 영향. 과제 2 컨슈머는 **종착 상태 도달 시 락 해제 훅**을 호출할 자리를 남겨둔다.

---

## 6. 테스트

- **단위**: `DeploymentRequested` 직렬화/역직렬화 라운드트립.
- **통합**: `@SpringBootTest` + 임베디드/Testcontainers RabbitMQ는 과함 → **발행→컨슈머 호출**을 mock 컨슈머로 검증하거나, 컨슈머 로직을 서비스로 분리해 메시지 없이 단위 테스트.
- 수동 확인: POST 배포 → management UI에서 `deploy.queue` 메시지 유입/소비 관찰.

---

## 7. 완료 체크리스트

- [ ] `spring-boot-starter-amqp` 추가 + compose RabbitMQ 연결 성공
- [ ] `deploy.queue` 선언, JSON 컨버터 적용
- [ ] `DeploymentPublisher` 실구현이 **커밋 후 발행**으로 동작 (스텁 제거/분리)
- [ ] 컨슈머가 `QUEUED→CLONING` 전이 후 Worker 진입점 호출, 멱등성 가드
- [ ] `./gradlew clean build` BUILD SUCCESSFUL

## 8. 하지 말 것

- DLQ / 재시도 백오프 / 다중 큐 (MVP 제외)
- 파이프라인 본체 구현 (과제 3)
- 엔티티 직렬화로 메시지 계약 대체 (record로 고정)
