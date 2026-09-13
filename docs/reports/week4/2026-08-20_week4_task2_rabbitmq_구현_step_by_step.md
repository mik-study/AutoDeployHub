# 4주차 과제 2번 — RabbitMQ 배선 구현 Step-by-Step

> 작성일: 2026-08-20
> 대상: 현수 (직접 구현용 상세 가이드)
> 선행 문서: `2026-06-16_week4_task2_rabbitmq_worker_consumer_현수.md` (개요 가이드)
> 이 문서는 **개요 가이드를 실제 코드 레벨까지 내린 실행 문서**다. 순서대로 따라가면 된다.

---

## 0. 이번 과제에서 확정한 4가지 결정

| # | 결정 항목 | 선택 | 이유 |
|---|---|---|---|
| 1 | Exchange 구조 | **Direct exchange + 바인딩** | 큐 추가 시 확장이 자연스럽고, management UI에서 exchange→queue 흐름이 눈에 보임 |
| 2 | 발행 시점 | **도메인 이벤트 + `@TransactionalEventListener(AFTER_COMMIT)`** | 커밋 전 발행으로 인한 "컨슈머가 없는 row 조회" 레이스를 구조적으로 제거 |
| 3 | 스텁 publisher | **삭제** | 실구현만 남겨 빈 충돌 없음. 테스트는 mock으로 대체 |
| 4 | 접속 설정 | **localhost 기본 + compose에서 env 오버라이드** | 기존 datasource가 쓰는 패턴과 동일 |

---

## 1. 완성 후 전체 흐름

```
[HTTP] POST /api/projects/{id}/deployments
   │
   ├─ DeploymentService.create()            @Transactional
   │    ├─ Deployment 저장 (PENDING → QUEUED)
   │    └─ eventPublisher.publishEvent(DeploymentRequested)   ← 아직 발행 안 됨. 예약만.
   │
   ├─ ★ 트랜잭션 COMMIT ★                    ← 여기서 DB에 row 확정
   │
   ├─ DeploymentEventListener                @TransactionalEventListener(AFTER_COMMIT)
   │    └─ DeploymentPublisher.publish()
   │         └─ RabbitDeploymentPublisher → rabbitTemplate.convertAndSend(
   │                deploy.exchange, deploy.request, msg)
   │
   └─ [HTTP 202 응답 반환]

┌─ 별도 스레드 (RabbitMQ 컨슈머) ─────────────────────────────┐
│ DeployQueueListener  @RabbitListener(queues="deploy.queue") │
│   ├─ lifecycleService.startProcessing(id)   @Transactional  │
│   │     · QUEUED 아니면 false 반환 → 조용히 종료 (멱등성)     │
│   │     · QUEUED 면 CLONING 전이 + markStarted()             │
│   └─ deploymentWorker.run(id)   ← 트랜잭션 밖. 과제 3 본체    │
└─────────────────────────────────────────────────────────────┘
```

**핵심 포인트 2개를 먼저 머리에 넣고 시작하자.**

1. **왜 커밋 후에 발행하는가** — 지금처럼 트랜잭션 안에서 발행하면, RabbitMQ는 밀리초 단위로 빠르기 때문에 **컨슈머가 `SELECT`를 날리는 시점에 아직 커밋이 안 끝나 있을 수 있다.** 그러면 `findById`가 empty를 반환하고 배포가 조용히 증발한다. 재현이 어렵고 로컬에선 거의 안 터지다가 부하가 걸리면 터지는 유형의 버그다.
2. **왜 파이프라인을 트랜잭션 밖에서 도는가** — 과제 3의 `docker build`는 수 분이 걸린다. 그걸 `@Transactional` 안에서 돌리면 DB 커넥션을 몇 분간 붙잡고 있게 되고, 커넥션 풀이 금방 마른다. 그래서 **상태 전이(짧은 트랜잭션)와 파이프라인 실행(트랜잭션 없음)을 분리**한다.

---

## 2. 만들/고칠 파일 목록

### 새로 만들 파일 (7개)

```
deployment/
 ├ DeploymentLifecycleService.java       # 워커 쪽 상태 전이 전용 서비스 (짧은 트랜잭션)
 ├ DeploymentWorker.java                 # 과제 3 진입점 (지금은 로그만 찍는 스텁)
 └ messaging/
     ├ DeployQueueConstants.java         # 큐/익스체인지/라우팅키 상수
     ├ DeploymentRequested.java          # 메시지 record (= 내부 이벤트 payload 겸용)
     ├ RabbitConfig.java                 # Queue/Exchange/Binding/MessageConverter 빈
     ├ RabbitDeploymentPublisher.java    # DeploymentPublisher 구현
     ├ DeploymentEventListener.java      # AFTER_COMMIT 훅
     └ DeployQueueListener.java          # @RabbitListener 컨슈머
```

> `DeploymentRequested`를 **내부 이벤트와 큐 메시지 양쪽에 재사용**한다. 필드가 완전히 같은데 record 두 개를 만드는 건 2인 MVP에서 과하다. 나중에 큐 계약에 `traceId`나 `messageVersion` 같은 게 붙어서 내부 이벤트와 모양이 갈라지는 순간에 분리하면 된다.

### 고칠 파일 (5개)

| 파일 | 변경 |
|---|---|
| `build.gradle` | `spring-boot-starter-amqp` 추가 |
| `src/main/resources/application.properties` | RabbitMQ 접속/리스너 설정 |
| `src/test/resources/application.properties` | **리스너 자동 기동 끄기** (안 하면 전체 테스트가 브로커 접속 재시도 로그로 뒤덮인다) |
| `docker-compose.yml` | backend 서비스에 `SPRING_RABBITMQ_*` env |
| `deployment/DeploymentPublisher.java` | 시그니처 `publish(Deployment)` → `publish(DeploymentRequested)` |
| `deployment/DeploymentService.java` | publisher 직접 호출 제거 → `ApplicationEventPublisher` 로 교체 |

### 삭제할 파일 (1개)

- `deployment/LoggingDeploymentPublisher.java`

---

## Step 1. 의존성 추가

`backend/autodeploy/build.gradle`

```gradle
dependencies {
    // ... 기존 유지
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

**테스트용 mockito 확인.** 지금 `build.gradle`에는 `spring-boot-starter-test`가 없고 모듈별 test 스타터만 쓰고 있어서, mockito가 클래스패스에 안 들어와 있을 수 있다. 먼저 확인부터:

```bash
./gradlew dependencies --configuration testRuntimeClasspath | grep -i mockito
```

안 나오면 추가한다 (버전은 Boot BOM이 관리하므로 생략):

```gradle
    testImplementation 'org.mockito:mockito-core'
    testImplementation 'org.mockito:mockito-junit-jupiter'
```

---

## Step 2. 설정 파일

### 2-1. `src/main/resources/application.properties`

기존 JWT 블록 아래에 추가:

```properties
# --- RabbitMQ ---
# 호스트에서 직접 띄울 때 기준. compose 로 띄울 때는 SPRING_RABBITMQ_HOST 로 오버라이드된다.
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=autodeploy_user
spring.rabbitmq.password=autodeploy_password

# 한 번에 메시지 1건만 받아 처리 (MVP: 프로젝트 동시 배포 방지는 과제 5 Redis 락이 담당)
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.concurrency=1
# 리스너에서 예외가 새어나가도 무한 재큐잉되지 않도록 하는 안전망. (DLQ 미도입)
spring.rabbitmq.listener.simple.default-requeue-rejected=false
```

> `SPRING_RABBITMQ_HOST` 같은 환경변수가 `spring.rabbitmq.host` 로 자동 매핑되는 건 Spring Boot의 **relaxed binding** 덕분이다. 지금 datasource가 compose에서 `SPRING_DATASOURCE_URL` 로 덮이는 것과 완전히 같은 방식.
>
> 비밀번호가 properties에 평문으로 들어가는데, 이 파일엔 이미 DB 비밀번호가 같은 방식으로 들어가 있어서 일관성은 맞다. 다만 **레포가 public 이면** 나중에 둘 다 env로 빼는 정리가 필요하다.

### 2-2. `src/test/resources/application.properties` ⚠️ 빠뜨리기 쉬움

```properties
# --- RabbitMQ (테스트에서는 브로커에 붙지 않는다) ---
# 이걸 안 켜면 @RabbitListener 컨테이너가 컨텍스트 로드 때 5672 접속을 계속 재시도하면서
# 테스트 로그를 AmqpConnectException 으로 도배한다.
spring.rabbitmq.listener.simple.auto-startup=false
```

**왜 이것만으로 충분한가:** `CachingConnectionFactory`는 lazy라서 아무도 안 쓰면 접속을 시도하지 않는다. 브로커에 실제로 붙는 건 (a) 리스너 컨테이너 기동, (b) `RabbitTemplate` 로 실제 발행 — 둘뿐이다. (a)는 위 설정으로 막고, (b)는 아래 Step 9에서 mock으로 막는다.

### 2-3. `docker-compose.yml`

`backend` 서비스의 `environment` 에 추가:

```yaml
  backend:
    environment:
      SPRING_DOCKER_COMPOSE_ENABLED: "false"
      SPRING_DATASOURCE_URL: jdbc:postgresql://host.docker.internal:5432/${POSTGRES_DB:-autodeploy}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-postgres}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-rlagustn1!}
      # 컨테이너 내부에서는 서비스명으로 접속 (autodeploy-network 안)
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: "5672"
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_DEFAULT_USER:-autodeploy_user}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_DEFAULT_PASS:-autodeploy_password}
    depends_on:
      rabbitmq:
        condition: service_healthy
```

> `depends_on` + `condition: service_healthy` 를 붙이면 RabbitMQ가 준비되기 전에 백엔드가 떠서 접속 실패 로그를 뿜는 걸 줄일 수 있다. rabbitmq 서비스에 이미 healthcheck가 정의돼 있으니 그대로 쓰면 된다.

---

## Step 3. 큐 토폴로지 상수

`deployment/messaging/DeployQueueConstants.java`

```java
package com.proj.autodeploy.deployment.messaging;

/**
 * deploy 큐 토폴로지 상수.
 *
 * <p>발행부(RabbitDeploymentPublisher)와 수신부(DeployQueueListener)가 반드시 같은 문자열을
 * 참조하도록 한 곳에서만 정의한다. 라우팅 키 오타는 예외 없이 조용히 메시지가 사라지는
 * 형태로 나타나기 때문에, 상수화가 사실상 필수다.
 */
public final class DeployQueueConstants {

    /** 배포 요청 큐. durable = 브로커가 재시작해도 큐 정의가 남는다. */
    public static final String QUEUE = "deploy.queue";

    /** direct exchange = 라우팅 키가 "정확히 일치"하는 바인딩으로만 전달. */
    public static final String EXCHANGE = "deploy.exchange";

    /** deploy.queue 로 향하는 라우팅 키. */
    public static final String ROUTING_KEY = "deploy.request";

    private DeployQueueConstants() {
    }
}
```

**Direct exchange를 고른 이유를 한 번 더 정리.** 나중에 `cleanup.queue`, `notification.queue` 가 붙을 때 exchange는 그대로 두고 라우팅 키만 늘리면 된다 (`deploy.request`, `deploy.cleanup`, …). Default exchange(큐 이름을 라우팅 키로 쓰는 방식)로 시작하면 그 시점에 전부 갈아엎어야 한다.

---

## Step 4. 메시지 계약

`deployment/messaging/DeploymentRequested.java`

```java
package com.proj.autodeploy.deployment.messaging;

/**
 * deploy.queue 메시지 계약. (공통 모임 합의 스키마)
 *
 * <p>용도가 둘이다.
 * <ul>
 *   <li>Spring 애플리케이션 이벤트 payload — DeploymentService 가 커밋 전에 발행</li>
 *   <li>RabbitMQ 메시지 본문 — AFTER_COMMIT 시점에 JSON 으로 직렬화되어 큐로</li>
 * </ul>
 *
 * <p><b>엔티티(Deployment)를 그대로 실어보내지 않는다.</b> 이유가 셋이다.
 * <ol>
 *   <li>엔티티는 커밋 후 detached 상태라 지연 로딩 필드 접근이 위험하다</li>
 *   <li>엔티티 필드가 바뀔 때마다 큐 메시지 포맷이 말없이 바뀐다 (계약이 깨진다)</li>
 *   <li>컨슈머가 필요로 하는 건 id 몇 개뿐인데 불필요하게 큰 페이로드가 된다</li>
 * </ol>
 */
public record DeploymentRequested(
        Long deploymentId,
        Long projectId,
        String branch,
        String commitHash
) {
}
```

> record가 JSON에서 역직렬화되려면 파라미터 이름이 바이트코드에 남아 있어야 하는데, Spring Boot Gradle 플러그인이 `-parameters` 컴파일 옵션을 자동으로 넣어주므로 신경 쓸 필요 없다.

---

## Step 5. RabbitConfig

`deployment/messaging/RabbitConfig.java`

```java
package com.proj.autodeploy.deployment.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * deploy 큐 토폴로지 선언.
 *
 * <p>여기 선언한 Queue/Exchange/Binding 빈은 RabbitAdmin 이 브로커 접속 시점에
 * 자동으로 declare 한다. 즉 management UI 에서 손으로 큐를 만들 필요가 없다.
 */
@Configuration
public class RabbitConfig {

    @Bean
    Queue deployQueue() {
        return QueueBuilder.durable(DeployQueueConstants.QUEUE).build();
    }

    @Bean
    DirectExchange deployExchange() {
        // (name, durable, autoDelete)
        return new DirectExchange(DeployQueueConstants.EXCHANGE, true, false);
    }

    @Bean
    Binding deployBinding(Queue deployQueue, DirectExchange deployExchange) {
        return BindingBuilder.bind(deployQueue)
                .to(deployExchange)
                .with(DeployQueueConstants.ROUTING_KEY);
    }

    /**
     * 기본 컨버터는 SimpleMessageConverter = 자바 직렬화라서 management UI 에서 본문이 안 읽힌다.
     * JSON 컨버터를 빈으로 등록해두면 Boot 자동설정이 RabbitTemplate 과 리스너 컨테이너
     * 양쪽에 알아서 적용해준다.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
```

### ⚠️ 확인 포인트 — 컨버터 클래스 이름

**Spring Boot 4는 Jackson 3(`tools.jackson`, 3.0.x)이 기본**이고, Jackson 2는 별도 모듈로 밀려났다. 그래서 Spring AMQP 4.x에서 JSON 컨버터 클래스 이름이 바뀌었다.

- 인터넷 자료 대부분에 나오는 **`Jackson2JsonMessageConverter`** — Jackson 2 시절 이름
- Boot 4 / Spring AMQP 4에서 쓸 것: **`JacksonJsonMessageConverter`**

IDE에서 `org.springframework.amqp.support.converter.` 까지 치고 자동완성으로 어느 쪽이 있는지 **직접 확인**하고 맞는 걸 쓰면 된다. 혹시 `Jackson2JsonMessageConverter` 만 있다면 그걸 쓰고, 이 문단은 무시해도 된다. (검색으로 나오는 예제 코드가 대부분 옛 이름이라, 이걸 모르면 `@Deprecated` 경고나 클래스 못 찾는 에러로 시간을 꽤 버린다.)

---

## Step 6. 발행 쪽 — 인터페이스 · 구현 · 커밋 훅

### 6-1. `DeploymentPublisher.java` (수정)

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.messaging.DeploymentRequested;

/**
 * 배포 작업 발행 seam.
 *
 * <p>과제 2에서 RabbitMQ 구현({@code RabbitDeploymentPublisher})으로 채웠다.
 * 인터페이스를 유지하는 이유는 테스트에서 브로커 없이 발행 여부만 검증하기 위해서다.
 *
 * <p>시그니처가 {@code Deployment} → {@code DeploymentRequested} 로 바뀐 이유:
 * 발행은 트랜잭션 커밋 <b>이후</b>에 일어나므로 그 시점의 엔티티는 detached 다.
 * 값만 담은 record 를 넘겨 경계를 명확히 한다.
 */
public interface DeploymentPublisher {

    void publish(DeploymentRequested message);
}
```

### 6-2. `deployment/messaging/RabbitDeploymentPublisher.java` (신규)

```java
package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitDeploymentPublisher implements DeploymentPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(DeploymentRequested message) {
        rabbitTemplate.convertAndSend(
                DeployQueueConstants.EXCHANGE,
                DeployQueueConstants.ROUTING_KEY,
                message);
        log.info("published deploy request: deploymentId={}, projectId={}, branch={}",
                message.deploymentId(), message.projectId(), message.branch());
    }
}
```

### 6-3. `deployment/messaging/DeploymentEventListener.java` (신규) — 이번 과제의 핵심

```java
package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "언제 발행할 것인가"를 담당. (어떻게 발행하는지는 RabbitDeploymentPublisher 의 몫)
 *
 * <p>AFTER_COMMIT 단계에서만 실행되므로, 컨슈머가 메시지를 받는 시점에는
 * deployments row 가 반드시 커밋되어 있다. 트랜잭션이 롤백되면 이 리스너는
 * 아예 호출되지 않는다 → "DB엔 없는데 큐엔 있는" 유령 메시지가 생기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentEventListener {

    private final DeploymentPublisher deploymentPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeploymentRequested(DeploymentRequested event) {
        try {
            deploymentPublisher.publish(event);
        } catch (Exception e) {
            // 여기서 예외를 던져봐야 이미 커밋도 끝났고 HTTP 202 응답도 나간 뒤다.
            // 브로커가 죽어 있으면 배포는 QUEUED 로 남는다. (사용자는 취소 가능)
            // TODO(과제 5): Redis 락 해제 + QUEUED 고아 배포 처리 정책을 여기서 함께 정한다.
            log.error("failed to publish deploy request. deploymentId={} (stays QUEUED)",
                    event.deploymentId(), e);
        }
    }
}
```

> **알아둘 한계 하나.** 발행에 실패해도 배포를 `FAILED` 로 못 바꾼다. `DeploymentStatus` 상태머신에서 `QUEUED` 는 `CLONING`/`CANCELED` 로만 갈 수 있기 때문이다 ([DeploymentStatus.java:37](../../../backend/autodeploy/src/main/java/com/proj/autodeploy/deployment/domain/DeploymentStatus.java)). 지금은 로그만 남기고 넘어가되, 과제 5에서 `QUEUED → FAILED` 전이를 상태머신에 추가할지 결정하자. 추가하면 `04_state_machine.md` 도 같이 고쳐야 한다.

### 6-4. `DeploymentService.java` (수정)

`DeploymentPublisher` 의존을 빼고 `ApplicationEventPublisher` 로 바꾼다.

```java
// import 추가
import com.proj.autodeploy.deployment.messaging.DeploymentRequested;
import org.springframework.context.ApplicationEventPublisher;

// 필드: DeploymentPublisher 제거 → 아래로 교체
private final ApplicationEventPublisher eventPublisher;
```

`create()` 메서드 끝부분:

```java
        deployment.transitionTo(DeploymentStatus.QUEUED);
        deployment = deploymentRepository.save(deployment);

        // 큐 발행은 커밋 이후에 일어난다. (DeploymentEventListener 가 AFTER_COMMIT 에서 수신)
        // 커밋 전에 보내면 컨슈머가 아직 없는 row 를 조회해 배포가 증발할 수 있다.
        eventPublisher.publishEvent(new DeploymentRequested(
                deployment.getId(),
                projectId,
                branch,
                deployment.getCommitHash()));

        return DeploymentDetailResponse.from(deployment);
```

기존 `// TODO(과제 2): ...` 주석은 지운다. `deployment.getId()` 가 null이 아닌 건 IDENTITY 전략이라 `save()` 시점에 INSERT가 나가면서 ID가 채워지기 때문이다.

### 6-5. `LoggingDeploymentPublisher.java` 삭제

---

## Step 7. 상태 전이 서비스 (컨슈머 전용)

`deployment/DeploymentLifecycleService.java` (신규)

**왜 `DeploymentService` 에 안 넣고 새로 만드는가.** `DeploymentService` 는 "사용자 요청" 경로다 — `userId` 를 받아 `ProjectService.getOwnedProject()` 로 소유권을 검증한다. 반면 워커 경로에는 사용자가 없다. 게다가 과제 3·4에서 단계별 상태 전이와 로그 적재 메서드가 계속 늘어날 자리라, 지금 분리해두는 편이 낫다.

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커(컨슈머) 경로의 배포 상태 전이 전용 서비스.
 *
 * <p>사용자 인증/소유권 검증이 없는 대신, 모든 메서드가 <b>짧은 트랜잭션</b>이어야 한다.
 * 실제 파이프라인(docker build 등)은 이 트랜잭션 바깥에서 돈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentLifecycleService {

    private final DeploymentRepository deploymentRepository;

    /**
     * 컨슈머 진입점. QUEUED → CLONING 전이 + 시작 시각 기록.
     *
     * <p>멱등성 가드를 겸한다. 같은 메시지를 두 번 받아도(브로커 재전송 등)
     * 두 번째는 QUEUED 가 아니므로 false 를 반환하고 아무것도 하지 않는다.
     *
     * @return 실제로 전이했으면 true, 이미 처리됐거나 대상이 없으면 false
     */
    @Transactional
    public boolean startProcessing(Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null) {
            log.warn("deployment not found. deploymentId={}", deploymentId);
            return false;
        }
        if (deployment.getStatus() != DeploymentStatus.QUEUED) {
            log.info("skip: status is {} (not QUEUED). deploymentId={}",
                    deployment.getStatus(), deploymentId);
            return false;
        }
        deployment.transitionTo(DeploymentStatus.CLONING);
        deployment.markStarted();
        return true;
    }

    /**
     * 파이프라인 실패 처리. 전이가 불가능한 상태(이미 종착 등)면 로그만 남기고 넘어간다.
     */
    @Transactional
    public void markFailed(Long deploymentId, String reason) {
        deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
            if (!deployment.getStatus().canTransitionTo(DeploymentStatus.FAILED)) {
                log.warn("cannot mark FAILED from {}. deploymentId={}",
                        deployment.getStatus(), deploymentId);
                return;
            }
            deployment.transitionTo(DeploymentStatus.FAILED);
            deployment.markFailureReason(reason);
        });
    }
}
```

> `transitionTo` 호출 후 `save()` 를 안 부르는 게 이상해 보일 수 있는데, `findById` 로 가져온 엔티티는 **영속 상태**라 트랜잭션 커밋 시 dirty checking으로 UPDATE가 자동으로 나간다. 과제 1의 `cancel()` 도 같은 방식이다.

---

## Step 8. 컨슈머

### 8-1. `deployment/DeploymentWorker.java` (과제 3 자리 확보)

```java
package com.proj.autodeploy.deployment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 배포 파이프라인 본체. <b>과제 3에서 구현한다.</b>
 *
 * <p>호출 규약 (과제 2에서 확정):
 * <ul>
 *   <li>호출 시점에 deployment 는 이미 CLONING 으로 전이돼 있다</li>
 *   <li>트랜잭션 밖에서 호출된다 — 내부에서 필요한 만큼 짧은 트랜잭션을 열 것</li>
 *   <li>예외를 던져도 된다 — 호출부(DeployQueueListener)가 FAILED 처리한다</li>
 * </ul>
 */
@Slf4j
@Component
public class DeploymentWorker {

    public void run(Long deploymentId) {
        // TODO(과제 3): git clone → Dockerfile 검사 → docker build → docker run → health check
        log.info("[TODO 과제 3] pipeline entry - deploymentId={}", deploymentId);
    }
}
```

### 8-2. `deployment/messaging/DeployQueueListener.java`

```java
package com.proj.autodeploy.deployment.messaging;

import com.proj.autodeploy.deployment.DeploymentLifecycleService;
import com.proj.autodeploy.deployment.DeploymentWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeployQueueListener {

    private final DeploymentLifecycleService lifecycleService;
    private final DeploymentWorker deploymentWorker;

    @RabbitListener(queues = DeployQueueConstants.QUEUE)
    public void onDeploymentRequested(DeploymentRequested message) {
        Long deploymentId = message.deploymentId();
        log.info("received deploy request: deploymentId={}", deploymentId);

        // 1) 멱등성 가드 + QUEUED → CLONING. 여기까지가 짧은 트랜잭션.
        if (!lifecycleService.startProcessing(deploymentId)) {
            return;   // 정상 ack. 재큐잉하지 않는다.
        }

        // 2) 파이프라인 본체는 트랜잭션 밖에서. (과제 3에서 수 분 걸릴 수 있음)
        try {
            deploymentWorker.run(deploymentId);
        } catch (Exception e) {
            log.error("deploy pipeline failed. deploymentId={}", deploymentId, e);
            lifecycleService.markFailed(deploymentId, e.getMessage());
            // 예외를 다시 던지지 않는다. DLQ 가 없는 상태에서 던지면
            // 브로커가 같은 메시지를 계속 재전달해 무한 루프가 된다.
        }
    }
}
```

**여기서 반드시 이해하고 넘어갈 것 3가지**

1. **`startProcessing` 을 왜 별도 빈에 두었나** — 같은 클래스 안의 `@Transactional` 메서드를 자기 자신이 호출하면 프록시를 안 거쳐서 **트랜잭션이 안 걸린다**(self-invocation 문제). `DeployQueueListener` → `DeploymentLifecycleService` 는 다른 빈이므로 프록시를 정상적으로 탄다.
2. **왜 `CLONING` 전이를 가장 먼저 하나** — 상태머신상 `QUEUED` 에서는 `FAILED` 로 갈 수 없다. 무슨 일이 생겨도 `FAILED` 로 정리할 수 있으려면 일단 `CLONING` 까지 올려놓아야 한다.
3. **예외를 삼키는 게 왜 맞나** — `spring.rabbitmq.listener.simple.default-requeue-rejected=false` 를 안전망으로 깔아뒀지만, 리스너에서 직접 잡아 `FAILED` 로 기록하는 편이 사용자에게 실패 이유가 남는다는 점에서 낫다. DLQ와 재시도 백오프는 MVP 범위 밖(4주차 "하지 말 것")이다.

---

## Step 9. 테스트 코드

기존 테스트를 깨지 않는 것 + 이번에 추가한 배선을 검증하는 것, 둘 다 필요하다.

### 9-0. 기존 테스트에 미치는 영향부터 정리

| 기존 테스트 | 영향 | 대응 |
|---|---|---|
| `AutodeployApplicationTests` (contextLoads) | 리스너 컨테이너가 브로커 접속 재시도 | Step 2-2의 `auto-startup=false` |
| `ApiIntegrationTest` | 동일 | 동일 |
| `DeploymentApiTest` | **영향 없음** — `@Transactional` 이라 테스트 트랜잭션이 롤백되고, AFTER_COMMIT 리스너는 애초에 실행되지 않는다 | 그대로 통과 |
| `DeploymentStatusTest` | 순수 단위 테스트 | 무관 |

> `DeploymentApiTest` 가 그냥 통과한다는 건 편하긴 한데, **뒤집어 말하면 이 테스트로는 발행 로직을 전혀 검증할 수 없다**는 뜻이기도 하다. `@Transactional` 테스트에서 AFTER_COMMIT이 안 도는 건 자주 헷갈리는 지점이니 확실히 짚고 가자. 그래서 아래 9-3을 따로 만든다.

### 9-1. 메시지 직렬화 라운드트립

`src/test/java/com/proj/autodeploy/deployment/messaging/DeploymentRequestedTest.java`

```java
package com.proj.autodeploy.deployment.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * 메시지 계약이 JSON 으로 온전히 왕복하는지 검증. 브로커 없이 컨버터만 단위 테스트한다.
 */
class DeploymentRequestedTest {

    private final MessageConverter converter = new JacksonJsonMessageConverter();

    @Test
    @DisplayName("직렬화 → 역직렬화 라운드트립에서 값이 보존된다")
    void roundTrip() {
        DeploymentRequested original = new DeploymentRequested(1L, 12L, "main", "abc1234");

        Message message = converter.toMessage(original, new MessageProperties());
        Object restored = converter.fromMessage(message);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("content-type 이 application/json 이다 (management UI 에서 본문이 읽힌다)")
    void contentTypeIsJson() {
        Message message = converter.toMessage(
                new DeploymentRequested(1L, 12L, "main", null), new MessageProperties());

        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    @DisplayName("commitHash 가 null 이어도 왕복된다 (수동 배포는 commitHash 없이 요청된다)")
    void nullCommitHash() {
        DeploymentRequested original = new DeploymentRequested(1L, 12L, "main", null);

        Object restored = converter.fromMessage(
                converter.toMessage(original, new MessageProperties()));

        assertThat(restored).isEqualTo(original);
    }
}
```

> 세 번째 케이스가 은근히 중요하다. `application.properties` 에 `spring.jackson.default-property-inclusion=non_null` 이 걸려 있어서 null 필드가 JSON에서 아예 빠진다. record 역직렬화 시 빠진 필드가 null로 잘 채워지는지 확인하는 의미가 있다. (이 설정은 Spring MVC 쪽 ObjectMapper에 걸리는 거라 AMQP 컨버터가 자체 인스턴스를 쓰면 영향이 없을 수도 있는데, 그렇더라도 검증해두면 손해 없다.)

### 9-2. Publisher 단위 테스트

`src/test/java/com/proj/autodeploy/deployment/messaging/RabbitDeploymentPublisherTest.java`

```java
package com.proj.autodeploy.deployment.messaging;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitDeploymentPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks RabbitDeploymentPublisher publisher;

    @Test
    @DisplayName("약속된 exchange/routingKey 로 메시지를 발행한다")
    void publishesToConfiguredExchangeAndRoutingKey() {
        DeploymentRequested message = new DeploymentRequested(1L, 12L, "main", "abc1234");

        publisher.publish(message);

        // (Object) 캐스팅: convertAndSend 오버로드가 많아 어느 시그니처인지 명시해준다
        verify(rabbitTemplate).convertAndSend(
                DeployQueueConstants.EXCHANGE,
                DeployQueueConstants.ROUTING_KEY,
                (Object) message);
    }
}
```

### 9-3. ★ AFTER_COMMIT 동작 검증 (이번 과제의 핵심 테스트)

`src/test/java/com/proj/autodeploy/deployment/messaging/DeploymentEventListenerTest.java`

```java
package com.proj.autodeploy.deployment.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.proj.autodeploy.deployment.DeploymentPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "커밋 후에만 발행된다"는 이번 과제의 핵심 계약을 못 박는 테스트.
 *
 * <p>DB 를 건드리지 않고 트랜잭션 경계와 이벤트 발행만 검증하므로 빠르고 안정적이다.
 * DeploymentPublisher 를 mock 으로 갈아끼워 RabbitMQ 브로커도 필요 없다.
 */
@SpringBootTest
class DeploymentEventListenerTest {

    @MockitoBean DeploymentPublisher deploymentPublisher;

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션이 커밋되면 발행된다 — 커밋 전에는 발행되지 않는다")
    void publishesOnlyAfterCommit() {
        DeploymentRequested event = new DeploymentRequested(1L, 12L, "main", "abc1234");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            // 아직 트랜잭션 안 — 발행되면 안 된다
            verifyNoInteractions(deploymentPublisher);
        });

        // 커밋 후 — 이제 발행돼야 한다
        verify(deploymentPublisher).publish(event);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 발행되지 않는다 — 유령 메시지 방지")
    void doesNotPublishOnRollback() {
        DeploymentRequested event = new DeploymentRequested(2L, 12L, "main", null);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(deploymentPublisher);
    }
}
```

**주의점**
- `@MockBean` 은 Spring Boot 4에서 제거됐다. **`@MockitoBean`** (`org.springframework.test.context.bean.override.mockito.MockitoBean`) 을 써야 한다. 검색하면 나오는 예제는 거의 다 옛 `@MockBean` 이라 그대로 복붙하면 컴파일이 안 된다.
- 이 테스트 클래스에는 **`@Transactional` 을 붙이면 안 된다.** 붙이는 순간 테스트 트랜잭션이 롤백돼서 AFTER_COMMIT이 영원히 안 돌고, 첫 번째 테스트가 실패한다. 지금 검증하려는 게 정확히 그 동작이다.
- `TransactionTemplate` 은 Boot가 자동 등록해주므로 그냥 주입받으면 된다.

### 9-4. 상태 전이 / 멱등성 테스트

`src/test/java/com/proj/autodeploy/deployment/DeploymentLifecycleServiceTest.java`

```java
package com.proj.autodeploy.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DeploymentLifecycleServiceTest {

    @Autowired DeploymentLifecycleService lifecycleService;
    @Autowired DeploymentRepository deploymentRepository;

    @Test
    @DisplayName("QUEUED 배포는 CLONING 으로 전이되고 시작 시각이 기록된다")
    void startProcessingFromQueued() {
        Long id = saveQueuedDeployment();

        boolean started = lifecycleService.startProcessing(id);

        assertThat(started).isTrue();
        Deployment found = deploymentRepository.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(DeploymentStatus.CLONING);
        assertThat(found.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("멱등성 — 같은 메시지를 두 번 받아도 두 번째는 false 이고 상태가 그대로다")
    void startProcessingIsIdempotent() {
        Long id = saveQueuedDeployment();
        lifecycleService.startProcessing(id);

        boolean secondCall = lifecycleService.startProcessing(id);

        assertThat(secondCall).isFalse();
        assertThat(deploymentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(DeploymentStatus.CLONING);
    }

    @Test
    @DisplayName("존재하지 않는 deploymentId 는 예외 없이 false 를 반환한다")
    void startProcessingMissingDeployment() {
        assertThat(lifecycleService.startProcessing(999_999L)).isFalse();
    }

    @Test
    @DisplayName("CLONING 배포는 FAILED 로 전이되고 실패 사유가 남는다")
    void markFailedFromCloning() {
        Long id = saveQueuedDeployment();
        lifecycleService.startProcessing(id);

        lifecycleService.markFailed(id, "docker build exited with 1");

        Deployment found = deploymentRepository.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(found.getFailureReason()).isEqualTo("docker build exited with 1");
        assertThat(found.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("QUEUED 에서는 FAILED 로 갈 수 없으므로 상태가 유지된다 (상태머신 가드)")
    void markFailedFromQueuedIsIgnored() {
        Long id = saveQueuedDeployment();

        lifecycleService.markFailed(id, "broker down");

        assertThat(deploymentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(DeploymentStatus.QUEUED);
    }

    private Long saveQueuedDeployment() {
        Deployment deployment = Deployment.builder()
                .projectId(1L)
                .branch("main")
                .build();
        deployment.transitionTo(DeploymentStatus.QUEUED);
        return deploymentRepository.save(deployment).getId();
    }
}
```

> 마지막 케이스가 Step 6-3에서 언급한 한계를 테스트로 못 박은 것이다. 나중에 상태머신에 `QUEUED → FAILED` 를 추가하기로 결정하면, **이 테스트가 빨간불로 바뀌면서 "여기도 같이 고쳐야 한다"고 알려준다.** 의도한 제약을 테스트로 남겨두는 게 이런 데서 값을 한다.

### 9-5. 컨슈머 분기 테스트

`src/test/java/com/proj/autodeploy/deployment/messaging/DeployQueueListenerTest.java`

```java
package com.proj.autodeploy.deployment.messaging;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proj.autodeploy.deployment.DeploymentLifecycleService;
import com.proj.autodeploy.deployment.DeploymentWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeployQueueListenerTest {

    @Mock DeploymentLifecycleService lifecycleService;
    @Mock DeploymentWorker deploymentWorker;
    @InjectMocks DeployQueueListener listener;

    private static final DeploymentRequested MESSAGE =
            new DeploymentRequested(1L, 12L, "main", "abc1234");

    @Test
    @DisplayName("QUEUED 상태였으면 워커 파이프라인을 호출한다")
    void runsWorkerWhenTransitionSucceeds() {
        when(lifecycleService.startProcessing(1L)).thenReturn(true);

        listener.onDeploymentRequested(MESSAGE);

        verify(deploymentWorker).run(1L);
    }

    @Test
    @DisplayName("이미 처리됐거나 없는 배포면 워커를 호출하지 않는다 (멱등성)")
    void skipsWorkerWhenTransitionFails() {
        when(lifecycleService.startProcessing(1L)).thenReturn(false);

        listener.onDeploymentRequested(MESSAGE);

        verify(deploymentWorker, never()).run(1L);
    }

    @Test
    @DisplayName("파이프라인이 예외를 던지면 FAILED 로 기록하고 예외를 삼킨다 (무한 재큐잉 방지)")
    void marksFailedAndSwallowsException() {
        when(lifecycleService.startProcessing(1L)).thenReturn(true);
        doThrow(new IllegalStateException("docker daemon not reachable"))
                .when(deploymentWorker).run(1L);

        assertThatNoException().isThrownBy(() -> listener.onDeploymentRequested(MESSAGE));

        verify(lifecycleService).markFailed(eq(1L), contains("docker daemon"));
    }
}
```

세 번째 테스트가 특히 중요하다. **예외가 리스너 밖으로 새어나가면 브로커가 같은 메시지를 무한 재전달**하는데, 그건 로컬에서 돌려보기 전엔 눈치채기 어렵다. 이 테스트가 그 계약을 지켜준다.

### 9-6. (선택) 실제 브로커 통합 테스트

`spring-boot-testcontainers` + `org.testcontainers:rabbitmq` 로 진짜 브로커를 띄워 발행→수신까지 검증할 수 있다. 다만 컨테이너 기동 때문에 테스트가 수십 초 단위로 느려지므로, **4주차에는 넣지 말고 Step 10의 수동 확인으로 갈음**하자. 5주차 이후 CI를 붙일 때 다시 보면 된다.

`spring-boot-starter-amqp-test` 라는 모듈도 Boot 4 BOM에 존재한다. 어떤 테스트 슬라이스를 제공하는지 확인해보고 쓸 만하면 도입해도 좋다.

---

## Step 10. 수동 검증 (E2E 눈으로 확인)

```bash
docker compose up -d rabbitmq
```

1. **management UI 접속** — http://localhost:15672 (`autodeploy_user` / `autodeploy_password`)
2. **백엔드 기동** 후 Exchanges 탭에 `deploy.exchange`, Queues 탭에 `deploy.queue` 가 **자동 생성**돼 있는지 확인. 없으면 `RabbitConfig` 빈이 안 잡혔거나 브로커 접속이 실패한 것이다.
3. **Queues → deploy.queue → Bindings** 에서 `deploy.exchange` 로부터 `deploy.request` 키로 바인딩돼 있는지 확인.
4. **배포 요청** — 프론트 [배포] 버튼 또는:
   ```bash
   curl -X POST http://localhost:8080/api/projects/1/deployments -H "Authorization: Bearer <토큰>" -H "Content-Type: application/json" -d "{}"
   ```
5. **로그 확인** — 아래 순서로 찍혀야 정상이다.
   ```
   published deploy request: deploymentId=1, ...      ← 발행 (커밋 후)
   received deploy request: deploymentId=1            ← 수신
   [TODO 과제 3] pipeline entry - deploymentId=1      ← 워커 진입
   ```
6. **DB 확인** — `deployments` 의 해당 row가 `CLONING` 이고 `started_at` 이 채워져 있으면 성공이다.

### 메시지가 안 보일 때 확인 순서

| 증상 | 확인할 곳 |
|---|---|
| 큐/익스체인지가 UI에 아예 안 생김 | 브로커 접속 실패. `spring.rabbitmq.host/username/password` 와 compose env |
| 발행 로그는 찍히는데 수신이 없음 | **라우팅 키 불일치**가 1순위. UI의 Bindings 탭과 `DeployQueueConstants` 비교 |
| 큐에 메시지가 쌓이기만 함 | 리스너 미기동. `auto-startup` 설정이 main properties에 잘못 들어갔는지 확인 |
| 발행 로그 자체가 없음 | 트랜잭션이 커밋되지 않았거나, `DeploymentEventListener` 가 빈으로 안 잡힘 |
| 같은 메시지가 무한 반복 수신 | 리스너에서 예외가 새어나가고 있음. try/catch 확인 |

---

## Step 11. 완료 체크리스트

- [ ] `spring-boot-starter-amqp` 추가, `./gradlew clean build` BUILD SUCCESSFUL
- [ ] `LoggingDeploymentPublisher` 삭제, `DeploymentPublisher` 시그니처 변경
- [ ] `DeploymentService` 가 `ApplicationEventPublisher` 로 이벤트만 발행
- [ ] `deploy.exchange` / `deploy.queue` / 바인딩이 management UI에 자동 생성됨
- [ ] 배포 요청 → 발행 → 수신 → `CLONING` 전이 → 워커 진입 로그까지 확인
- [ ] 테스트 6종 통과 (`DeploymentRequestedTest`, `RabbitDeploymentPublisherTest`, `DeploymentEventListenerTest`, `DeploymentLifecycleServiceTest`, `DeployQueueListenerTest`, 기존 4종)
- [ ] 테스트 로그에 `AmqpConnectException` 이 안 보임 (test properties `auto-startup=false` 확인)

## Step 12. 하지 말 것 (4주차 범위 유지)

- DLQ / 재시도 백오프 / 다중 큐
- 파이프라인 본체 구현 — 과제 3
- Redis 락 — 과제 5 (지금은 `DeploymentService` 의 DB 기반 가드 그대로 둔다)

---

## 부록. 과제 3·5로 넘길 메모

| 항목 | 내용 |
|---|---|
| 과제 3 | `DeploymentWorker.run(Long)` 시그니처 확정. 트랜잭션 밖 호출 / 예외 던져도 됨이 계약 |
| 과제 3 | 단계별 상태 전이 + `DeploymentLog` 적재 메서드는 `DeploymentLifecycleService` 에 추가 |
| 과제 5 | 발행 실패 시 `QUEUED` 고아 배포 처리 — 상태머신에 `QUEUED → FAILED` 를 추가할지 결정 (추가 시 `04_state_machine.md` 동반 수정) |
| 과제 5 | Redis 락 해제 훅 위치 — `DeployQueueListener` 의 종착 도달 지점 |
| 과제 4 | SSE 는 `DeploymentLog` 적재 시점에 이벤트를 흘려야 하므로, 과제 3의 로그 적재 메서드에 훅 자리를 남겨둘 것 |
