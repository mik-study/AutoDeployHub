# 3주차 백엔드 소스코드 상세 설명서 (현수)

> AutoDeployHub `backend/autodeploy` 모듈에 3주차에 추가/변경한 **모든 소스코드를 파일 단위로** 상세 설명한다.
> 설계 문서(`03_erd.md`, `04_state_machine.md`, `05_api_spec.md`)가 실제 코드로 어떻게 구현됐는지 1:1로 추적할 수 있게 작성.
>
> - 작성일: 2026-06-09
> - 담당: 김현수
> - 언어/프레임워크: **Java 21 + Spring Boot 4.0.6 + Gradle**
> - 빌드: `./gradlew clean build` → ✅ BUILD SUCCESSFUL (28개 테스트 통과)

---

## 0. 패키지 구조 (전체 지도)

단일 모듈 + feature 패키지 구조. `com.proj.autodeploy` 하위:

```
com.proj.autodeploy
├── AutodeployApplication.java         # 진입점 (Kotlin → Java 전환)
│
├── global/                            # 공통 인프라
│   ├── config/
│   │   └── SecurityConfig.java        # Security 필터체인, CORS, PasswordEncoder
│   ├── security/
│   │   ├── JwtProperties.java         # jwt.* 설정 바인딩
│   │   ├── JwtTokenProvider.java      # JWT 발급/검증
│   │   ├── JwtAuthenticationFilter.java  # Bearer 토큰 → 인증 주입
│   │   └── AuthPrincipal.java         # 인증 주체(userId, email)
│   ├── error/
│   │   ├── ErrorCode.java             # 에러코드 enum (+HTTP status)
│   │   ├── ApiException.java          # 비즈니스 예외
│   │   ├── ErrorResponse.java         # 에러 응답 포맷
│   │   └── GlobalExceptionHandler.java
│   └── response/
│       ├── ApiResponse.java           # { data }
│       └── PagedResponse.java         # { data, page }
│
├── user/         User, UserStatus, UserRepository
├── auth/         AuthController, AuthService, dto(5)
├── project/      Project, ProjectStatus, BuildType, Repository, Controller, Service, dto(5)
├── environment/  EnvironmentVariable, Repository
├── deployment/   Deployment, DeploymentLog, DeploymentStatus, DeploymentTriggerType, LogLevel, Repository(2)
├── runtime/      RuntimeInstance, RuntimeColor, RuntimeStatus, Repository
└── webhook/      Webhook, Repository
```

설계 원칙:
- **엔티티는 `domain` 하위 패키지**에, repository/service/controller/dto는 feature 루트에 배치.
- 엔티티는 **setter 없이** 의미 있는 메서드(`transitionTo`, `archive`, `patch` 등)로만 상태 변경.
- Lombok `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@Builder(private 생성자)` 패턴.

### 0.1 설계 방식: 왜 계층(MVC)별이 아니라 도메인별인가

본 프로젝트는 전통적인 **패키지 바이 레이어(package-by-layer, 계층별)** 가 아니라
**패키지 바이 피처(package-by-feature, 기능/도메인별)** 구조를 채택했다.

**(A) 전통적 계층별 — 역할(Controller/Service/Repository)이 1차 분류**
```
com.proj.autodeploy
├── controller/   UserController, ProjectController, DeploymentController, ...
├── service/      UserService, ProjectService, DeploymentService, ...
├── repository/   UserRepository, ProjectRepository, ...
├── domain/       User, Project, Deployment, ...
└── dto/          (모든 DTO 한 곳)
```

**(B) 본 프로젝트: 기능/도메인별 — 주제(project/deployment/…)가 1차 분류**
```
com.proj.autodeploy
├── auth/ user/ project/ deployment/ runtime/ environment/ webhook/   ← 도메인 슬라이스
│        (각 폴더 안에 Controller·Service·Repository·domain·dto 가 함께)
└── global/   config·security·error·response                          ← 횡단 관심사
```

> **오해 방지**: MVC를 버린 것이 아니다. Controller → Service → Repository 계층은 **그대로 존재**한다.
> 계층을 "가로로" 자르던 것을 도메인 기준으로 "세로로" 슬라이스했을 뿐이며, 각 feature 폴더를 열면 익숙한 MVC 계층이 그대로 보인다.

### 0.2 이렇게 설계한 합리적 근거

| # | 근거 | 설명 |
|---|---|---|
| ① | **높은 응집도 / 낮은 결합도** | "프로젝트" 관련 코드(컨트롤러·서비스·레포·엔티티·DTO)가 `project/` 한 폴더에 모임. 기능 하나를 이해/수정할 때 폴더 하나만 보면 됨. 계층별 구조는 한 기능 수정에 `controller/`·`service/`·`repository/` 3곳을 오가야 함 |
| ② | **설계 문서와 1:1 정합** | `03_erd.md`·`04_state_machine.md`·`05_api_spec.md` 가 모두 도메인 단위(User/Project/Deployment/Runtime/Webhook)로 작성됨 → 같은 도메인으로 패키지를 나누면 **문서 ↔ 코드 추적성**이 그대로 유지 |
| ③ | **Worker 모듈 분리 용이 (결정적)** | 추후 `autodeploy-api` / `autodeploy-worker` 멀티모듈 분리 가능성(01·02 문서). 빌드/배포 로직이 `deployment/`·`runtime/`에 모여 있어 **폴더째 들어내면** 분리 완료. 계층별이면 controller/service/repository를 가로질러 잘라야 해 고통스러움 |
| ④ | **확장 시 탐색성 유지** | 규모가 커지면 계층별의 `service/`는 무관한 서비스가 쌓이는 "잡동사니 서랍"이 됨. feature 구조는 도메인 수만큼만 폴더가 늘어 탐색 난이도가 일정 |
| ⑤ | **경계(bounded context) 캡슐화** | feature 내부 전용 클래스는 package-private로 은닉 가능 → 도메인 간 의도치 않은 의존 감소. 엔티티+enum을 각 도메인 `domain/` 하위에 둔 것도 같은 맥락(가벼운 DDD) |
| ⑥ | **2인 협업** | 도메인 단위로 작업이 나뉘어 같은 파일/폴더 충돌(merge conflict) 감소 |

특히 우리 프로젝트 맥락에서는 **②(문서 기반 개발)** 와 **③(추후 멀티모듈 분리)** 가 결정적 근거다.

### 0.3 명칭 · 한계 · 트레이드오프

- 정식 명칭: **Package-by-feature**(도메인형 패키징). 풀 DDD(application/infrastructure/presentation 레이어 분리, 헥사고날)까지는 아니며, 그 방향으로 가는 **실용적 중간 형태**다.
- `global/`은 어느 도메인에도 속하지 않는 **횡단 관심사**(보안·에러·응답 포맷)를 모은 곳.
- 트레이드오프: 엔티티 1~2개 수준의 아주 단순한 CRUD라면 계층별이 더 간단할 수 있다. 그러나 본 프로젝트는 **도메인 7개 + Worker 분리 가능성**이 있어 feature 방식이 명확히 유리하다.

> 한 줄 요약: **"MVC를 버린 것이 아니라, MVC를 도메인별로 슬라이스했다."**

---

## 1. 빌드 / 설정 변경

### 1.1 `build.gradle`

**핵심 변경: Kotlin → Java 플러그인 전환 + 의존성 추가.**

| 구분 | 내용 |
|---|---|
| 플러그인 | `org.jetbrains.kotlin.*` 3종 제거 → `java` 추가. `org.springframework.boot 4.0.6`, `io.spring.dependency-management 1.1.7` 유지 |
| Java | `toolchain { languageVersion = 21 }` |
| Lombok | `compileOnly` + `annotationProcessor` + `configurations.compileOnly.extendsFrom annotationProcessor` |
| 신규 의존성 | `spring-boot-starter-validation` (Bean Validation) |
| JWT | `io.jsonwebtoken:jjwt-api:0.12.6` (impl/gson 은 runtime), `com.google.code.gson:gson` (runtime) |
| 테스트 | `junit-jupiter`, `assertj-core`, `com.jayway.jsonpath:json-path`, `com.h2database:h2`(runtime) |

> **왜 jjwt-gson?** Spring Boot 4는 Jackson 3(`tools.jackson`)를 쓴다. jjwt-jackson(=Jackson 2)을 넣으면 버전 충돌 우려가 있어, JSON 직렬화를 **Gson** 으로 처리하는 `jjwt-gson`을 선택. 코드는 `jjwt-api`만 참조하고 직렬화기는 런타임 ServiceLoader가 자동 선택.

### 1.2 `src/main/resources/application.properties`

```properties
spring.application.name=autodeploy
spring.jpa.hibernate.ddl-auto=update          # 로컬 dev: 스키마 자동 반영
spring.jpa.open-in-view=false                  # OSIV 비활성(지연로딩 누수 방지)
spring.jpa.properties.hibernate.format_sql=true
spring.jackson.default-property-inclusion=non_null   # null 필드 직렬화 제외(에러 details 등)
management.endpoints.web.exposure.include=health,info,prometheus
management.graphite.metrics.export.enabled=false      # 로컬에 graphite 없음 → export 끔
jwt.secret=${JWT_SECRET:local-dev-secret-...}  # 운영은 환경변수 주입
jwt.access-token-validity-seconds=1800
jwt.refresh-token-validity-seconds=1209600
```

### 1.3 `src/test/resources/application.properties` (신규)

테스트는 외부 DB/Docker 없이 **H2(PostgreSQL 호환 모드)** 로 동작.

```properties
spring.docker.compose.enabled=false
spring.datasource.url=jdbc:h2:mem:autodeploy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.hibernate.ddl-auto=create-drop
management.graphite.metrics.export.enabled=false
jwt.secret=test-secret-key-for-junit-context-load-0123456789abcdef
jwt.access-token-validity-seconds=1800
jwt.refresh-token-validity-seconds=1209600
```

### 1.4 `AutodeployApplication.java`

Kotlin `AutodeployApplication.kt` → Java 로 전환.

```java
@SpringBootApplication
public class AutodeployApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutodeployApplication.class, args);
    }
}
```

---

## 2. enum (8종)

### 2.1 `deployment/domain/DeploymentStatus.java` ⭐ (상태머신 핵심)

15개 상태 + **전이 규칙을 코드로 강제**. `04_state_machine.md` 그대로.

```java
public enum DeploymentStatus {
    PENDING, QUEUED, CLONING, CHECKING_DOCKERFILE, BUILDING, PUSHING_IMAGE,
    DEPLOYING, HEALTH_CHECKING, SWITCHING_TRAFFIC,  // Blue-Green
    SUCCEEDED, FAILED, CANCELED, ROLLING_BACK, ROLLED_BACK, ROLLBACK_FAILED;

    private static final Map<DeploymentStatus, Set<DeploymentStatus>> TRANSITIONS =
            new EnumMap<>(DeploymentStatus.class);
    static {
        TRANSITIONS.put(PENDING, EnumSet.of(QUEUED, FAILED, CANCELED));
        TRANSITIONS.put(QUEUED, EnumSet.of(CLONING, CANCELED));
        // ... 각 단계 → 다음 단계 / FAILED
        TRANSITIONS.put(SWITCHING_TRAFFIC, EnumSet.of(SUCCEEDED, FAILED));
        TRANSITIONS.put(FAILED, EnumSet.of(ROLLING_BACK));   // 명시적 롤백만 허용
        TRANSITIONS.put(ROLLING_BACK, EnumSet.of(ROLLED_BACK, ROLLBACK_FAILED));
        // SUCCEEDED/CANCELED/ROLLED_BACK/ROLLBACK_FAILED → 빈 집합(종착)
    }

    public boolean isTerminal()  { return this==SUCCEEDED||this==FAILED||this==CANCELED
                                          ||this==ROLLED_BACK||this==ROLLBACK_FAILED; }
    public boolean isInProgress(){ return !isTerminal() && this!=PENDING; }
    public boolean canTransitionTo(DeploymentStatus next){
        return next!=null && TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
    public Set<DeploymentStatus> nextStates(){ /* 불변 집합 반환 */ }
}
```

**포인트**: `FAILED → ROLLING_BACK` 만 허용 — Blue-Green에서 "실패해도 트래픽은 기존 Active 유지"이므로 자동 롤백 상태는 두지 않고, 사용자가 명시적으로 요청할 때만 롤백 흐름으로 진입.

### 2.2 `runtime/domain/RuntimeColor.java`

```java
public enum RuntimeColor {
    BLUE, GREEN;
    public RuntimeColor opposite() { return this==BLUE ? GREEN : BLUE; }
    public int defaultPort()       { return this==BLUE ? 8081 : 8082; }
}
```

### 2.3 나머지 enum

| enum | 값 | 위치 |
|---|---|---|
| `DeploymentTriggerType` | MANUAL, WEBHOOK, ROLLBACK | deployment/domain |
| `LogLevel` | INFO, WARN, ERROR | deployment/domain |
| `RuntimeStatus` | STARTING, RUNNING, STOPPED, FAILED | runtime/domain |
| `UserStatus` | ACTIVE, SUSPENDED, DELETED | user/domain |
| `ProjectStatus` | ACTIVE, ARCHIVED | project/domain |
| `BuildType` | DOCKERFILE (MVP는 1종) | project/domain |

---

## 3. 엔티티 (7종) — `03_erd.md` 1:1

공통 패턴: `@Entity` + `@Table(인덱스 명시)` + `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@Builder` private 생성자. 시각은 Hibernate `@CreationTimestamp`/`@UpdateTimestamp` (별도 설정 불필요).

### 3.1 `user/domain/User.java`

```java
@Entity @Table(name="users",
  indexes=@Index(name="idx_users_email", columnList="email", unique=true))
public class User {
    @Id @GeneratedValue(strategy=IDENTITY) Long id;
    @Column(nullable=false, unique=true) String email;
    @Column(name="password_hash", nullable=false) String passwordHash;  // BCrypt
    String name;
    @Enumerated(STRING) UserStatus status;   // 기본 ACTIVE
    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp  Instant updatedAt;
    // changeName(), changePassword() 메서드로만 변경
}
```

### 3.2 `project/domain/Project.java` (가장 로직이 많음)

- 컬럼: ownerId, name, description, repositoryUrl, defaultBranch(기본 main), rootDirectory(기본 /), buildType(기본 DOCKERFILE), healthCheck 4종, **subdomain(unique)**, webhookSecret, status.
- 인덱스: `idx_projects_owner_id`, `uk_projects_subdomain`.
- 기본값은 빌더 생성자에서 null이면 채움.
- 주요 메서드:
  - `isOwnedBy(userId)` — 소유권 검증
  - `assignSubdomain(s)` — subdomain 강제 지정 (자동생성 로직에서 사용, **§7.2 버그 수정** 참고)
  - `patch(...)` — null 아닌 필드만 부분 수정. **repositoryUrl/subdomain은 파라미터에 아예 없음**(변경 불가)
  - `archive()` — status=ARCHIVED (soft delete)

### 3.3 `environment/domain/EnvironmentVariable.java`

```java
@Table(indexes=@Index(name="uk_env_project_key",
                      columnList="project_id, env_key", unique=true))
...
@Column(name="env_key") String key;   // 'key'는 DB 예약어 → env_key 로 매핑
@Column(name="encrypted_value", columnDefinition="TEXT") String encryptedValue;
boolean secret;                         // 로그 마스킹 여부
```

### 3.4 `deployment/domain/Deployment.java` ⭐ (상태머신 보유)

```java
@Table(indexes={
  @Index(name="idx_deployments_project_created", columnList="project_id, created_at"),
  @Index(name="idx_deployments_status", columnList="status")})
public class Deployment {
    ... projectId, previousDeploymentId, branch, commitHash, commitMessage,
        imageRepository, imageTag, status, triggerType, failureReason,
        startedAt, finishedAt, createdAt ...

    public void transitionTo(DeploymentStatus next) {        // ⭐ 상태 전이 강제
        if (!this.status.canTransitionTo(next))
            throw new IllegalStateException("Illegal transition: "+status+" -> "+next);
        this.status = next;
        if (next.isTerminal()) this.finishedAt = Instant.now();
    }
    public void markStarted()  { this.startedAt = Instant.now(); }
    public void markFailureReason(String r) { this.failureReason = r; }
}
```

### 3.5 `deployment/domain/DeploymentLog.java`

deploymentId, sequence, level(LogLevel), message(TEXT), createdAt. 인덱스 `(deployment_id, sequence)` — SSE 스트리밍 순서 조회용.

### 3.6 `runtime/domain/RuntimeInstance.java` ⭐ (Blue-Green)

```java
@Table(indexes={
  @Index(name="uk_runtime_container_name", columnList="container_name", unique=true),
  @Index(name="idx_runtime_project_active", columnList="project_id, is_active")})
public class RuntimeInstance {
    ... projectId, deploymentId, containerName(unique), imageTag,
    @Enumerated(STRING) RuntimeColor color;   // BLUE/GREEN
    int port;                                  // 8081/8082
    @Column(name="is_active") boolean active;  // 현재 트래픽 받는 쪽
    @Enumerated(STRING) RuntimeStatus status;
    Instant startedAt, stoppedAt;

    public void activate(){ active=true; }
    public void deactivate(){ active=false; }
    public void changeStatus(RuntimeStatus s){ status=s; if(STOPPED||FAILED) stoppedAt=now; }
}
```

### 3.7 `webhook/domain/Webhook.java`

projectId, eventType, **deliveryId(unique → 중복 수신 방지)**, payload(TEXT), signatureValid, processed, createdDeploymentId, receivedAt. `markProcessed(depId)` 메서드.

---

## 4. Repository (7종)

Spring Data JPA 인터페이스. 쿼리는 메서드명 파생.

| Repository | 주요 메서드 |
|---|---|
| `UserRepository` | `findByEmail`, `existsByEmail` |
| `ProjectRepository` | `findByOwnerIdAndStatus(…, Pageable)`, `existsBySubdomain` |
| `EnvironmentVariableRepository` | `findByProjectId`, `findByIdAndProjectId`, `existsByProjectIdAndKey` |
| `DeploymentRepository` | `findByProjectId(Pageable)` |
| `DeploymentLogRepository` | `findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc` |
| `RuntimeInstanceRepository` | `findByProjectId`, `findByProjectIdAndActiveTrue` |
| `WebhookRepository` | `findByDeliveryId`, `existsByDeliveryId` |

---

## 5. global (공통 인프라)

### 5.1 응답 포맷 — `05_api_spec.md §0.1`

```java
public record ApiResponse<T>(T data) {                       // { "data": ... }
    public static <T> ApiResponse<T> of(T d){ return new ApiResponse<>(d); }
}
public record PagedResponse<T>(List<T> data, PageMeta page) {// { "data":[...], "page":{...} }
    public record PageMeta(int page,int size,long totalElements,int totalPages){}
    public static <T> PagedResponse<T> of(Page<T> p){ ... }
}
```

### 5.2 에러 처리 — `05_api_spec.md §0.4`

- **`ErrorCode`**: enum. 각 코드가 `HttpStatus` + 기본 메시지 보유 (AUTH_INVALID_CREDENTIALS=401, USER_EMAIL_DUPLICATED=409, PROJECT_NOT_FOUND=404, PROJECT_ACCESS_DENIED=403, … VALIDATION_ERROR=400, INTERNAL_ERROR=500 등 명세 전체).
- **`ApiException`**: `RuntimeException` + `ErrorCode` + `details(Map)`. `ApiException.of(code, key, value)` 헬퍼.
- **`ErrorResponse`**: `record(Body error)`, `Body(code, message, details)`. null은 `default-property-inclusion=non_null`로 생략.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`):
  - `ApiException` → 해당 status + 코드/메시지/details
  - `MethodArgumentNotValidException` → 400 VALIDATION_ERROR + 필드별 메시지
  - `AccessDeniedException` → 403, `AuthenticationException` → 401
  - `Exception` → 500 INTERNAL_ERROR

### 5.3 보안 — JWT

**`JwtProperties`** (`@ConfigurationProperties("jwt")`): secret, accessTokenValiditySeconds, refreshTokenValiditySeconds.

**`AuthPrincipal`** `record(Long userId, String email)` — SecurityContext의 principal. 컨트롤러에서 `@AuthenticationPrincipal`로 주입.

**`JwtTokenProvider`** (jjwt 0.12 API):
```java
key = Keys.hmacShaKeyFor(secret.getBytes(UTF_8));   // HS256
createAccessToken(userId,email): subject=userId, claim email/type=access, exp=+1800s
createRefreshToken(userId):       subject=userId, claim type=refresh, exp=+14d
parse(token): Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
isAccessToken / isRefreshToken: type claim 검사
```

**`JwtAuthenticationFilter`** (`OncePerRequestFilter`):
- `Authorization: Bearer xxx` 파싱 → `parse()` → access 토큰이면 `AuthPrincipal`로 `UsernamePasswordAuthenticationToken` 생성 후 SecurityContext에 주입.
- 토큰 없거나 검증 실패 시 **인증 미설정 후 통과**(보호 자원이면 EntryPoint가 401). 예외를 필터에서 던지지 않음(필터 예외는 advice가 못 잡으므로).

**`SecurityConfig`**:
```java
http.cors(withDefaults())             // CorsConfigurationSource 빈 사용
    .csrf(disable).formLogin(disable).httpBasic(disable).logout(disable)
    .sessionManagement(STATELESS)
    .authorizeHttpRequests(
        permitAll: /api/auth/**, /api/webhooks/**, /swagger-ui/**, /v3/api-docs/**, /actuator/**
        anyRequest authenticated)
    .exceptionHandling(401 EntryPoint / 403 AccessDeniedHandler → 공통 에러 JSON 직접 작성)
    .addFilterBefore(JwtAuthenticationFilter, UsernamePasswordAuthenticationFilter)
@Bean PasswordEncoder = BCryptPasswordEncoder
@Bean CorsConfigurationSource: origin localhost:5173/3000, GET/POST/PATCH/PUT/DELETE/OPTIONS, /api/**
```

---

## 6. auth (인증 기능) — `05_api_spec.md §1`

### 6.1 DTO (5종, 모두 record + Bean Validation)

| DTO | 필드/검증 |
|---|---|
| `SignupRequest` | email(@Email), password(@Size 8~64), name(@NotBlank) |
| `SignupResponse` | userId, email, name (`from(User)`) |
| `LoginRequest` | email, password (@NotBlank) |
| `RefreshRequest` | refreshToken (@NotBlank) |
| `TokenResponse` | accessToken, refreshToken, accessTokenExpiresIn |

### 6.2 `AuthService`

```java
@Transactional signup(req):
   existsByEmail → USER_EMAIL_DUPLICATED(409)
   User.builder().passwordHash(encoder.encode(pw))… save
@Transactional(readOnly) login(req):
   findByEmail || AUTH_INVALID_CREDENTIALS(401)
   encoder.matches || AUTH_INVALID_CREDENTIALS(401)
   issueTokens()
@Transactional(readOnly) refresh(refreshToken):
   parse → ExpiredJwtException→AUTH_TOKEN_EXPIRED / 그 외→AUTH_TOKEN_INVALID
   isRefreshToken 확인 → findById → issueTokens()
issueTokens(user): access+refresh+expiresIn
```

### 6.3 `AuthController` (`/api/auth`)

`POST /signup`(201) · `POST /login`(200) · `POST /refresh`(200) · `POST /logout`(204, stateless no-op). 모두 `ApiResponse`로 감쌈, `@Valid` 적용.

---

## 7. project (프로젝트 CRUD) — `05_api_spec.md §2`

### 7.1 DTO (5종)

| DTO | 설명 |
|---|---|
| `CreateProjectRequest` | name, description, repositoryUrl(@Pattern github), …, subdomain(@Pattern 소문자/숫자/하이픈, 빈값 허용) |
| `UpdateProjectRequest` | PATCH용. **repositoryUrl/subdomain 필드 없음**(변경 불가) |
| `CreateProjectResponse` | 생성 응답. webhookUrl + **webhookSecret 평문(1회)** |
| `ProjectResponse` | 상세. **webhookSecret = `whs_****` 마스킹** |
| `ProjectSummaryResponse` | 목록용. lastDeployment는 4주차 전까지 null |

### 7.2 `ProjectService` — ⭐ subdomain 자동생성 (버그 수정 포함)

```java
@Transactional create(userId, req):
   requested = normalize(req.subdomain())            // 빈문자→null
   if requested!=null && existsBySubdomain → PROJECT_SUBDOMAIN_DUPLICATED(409)
   plainSecret = "whs_" + 랜덤 hex(24바이트)

   // ⚠️ subdomain은 NOT NULL+UNIQUE. IDENTITY 전략이라 save() 즉시 INSERT됨.
   //    미지정 시 null로 INSERT 불가 → 임시 placeholder로 INSERT 후 project-{id}로 갱신
   autoGenerate = (requested==null)
   initial = autoGenerate ? "pending-"+UUID(16자) : requested
   project = save(builder…subdomain(initial)…webhookSecret(plainSecret))
   if autoGenerate: project.assignSubdomain("project-"+project.getId())
   return CreateProjectResponse.from(project, WEBHOOK_URL, plainSecret)

list(userId, pageable):  findByOwnerIdAndStatus(ACTIVE) → ProjectSummaryResponse
get/update/delete:       getOwnedProject()로 소유권 검증
   getOwnedProject: findById||PROJECT_NOT_FOUND(404); !isOwnedBy→PROJECT_ACCESS_DENIED(403)
update: project.patch(...) (부분 수정)
delete: project.archive() (soft delete)
```

> **§7.2 버그**: 초기 구현은 `subdomain=null`로 save 후 갱신하려 했으나, `@GeneratedValue(IDENTITY)`는 id 확보를 위해 save 시점에 **즉시 INSERT** → NOT NULL 위반. 임시 placeholder(`pending-{uuid}`)로 INSERT 후 `project-{id}`로 UPDATE하도록 수정. 이 버그는 §8.2 통합테스트가 잡아냄.

### 7.3 `ProjectController` (`/api/projects`)

`POST`(201) · `GET`(목록, `@PageableDefault(20)`) · `GET /{id}` · `PATCH /{id}` · `DELETE /{id}`(204). 사용자 식별은 `@AuthenticationPrincipal AuthPrincipal`.

---

## 8. 테스트

### 8.1 `deployment/domain/DeploymentStatusTest.java` (단위, Spring 불필요)

- 정상 흐름 PENDING→…→SUCCEEDED 전이 허용
- 진행 단계 → FAILED 허용
- SUCCEEDED 종착(어떤 전이도 거부, nextStates 비어있음)
- FAILED → ROLLING_BACK만 허용
- ROLLING_BACK → ROLLED_BACK/ROLLBACK_FAILED만 허용
- null 전이 거부
- `@ParameterizedTest @EnumSource`로 isTerminal/isInProgress 정확성

### 8.2 `api/ApiIntegrationTest.java` (`@SpringBootTest @AutoConfigureMockMvc @Transactional`, H2)

MockMvc + jsonPath로 6종 검증:
1. 회원가입(201)→로그인(200, accessToken 존재)
2. 잘못된 비번 → 401 AUTH_INVALID_CREDENTIALS
3. 중복 이메일 → 409 USER_EMAIL_DUPLICATED
4. 프로젝트 생성(secret `whs_` 평문)·조회(secret 마스킹)·목록(page 메타)
5. 토큰 없이 접근 → 401
6. subdomain 미지정 → `project-` 자동 생성

> Spring Boot 4에서 `@AutoConfigureMockMvc`가 `org.springframework.boot.webmvc.test.autoconfigure`로 이동 → 임포트 수정 필요했음.

---

## 9. 구현 중 마주친 이슈 / 해결 (정리)

| # | 이슈 | 원인 | 해결 |
|---|---|---|---|
| 1 | 컨텍스트 로드 시 DataSource 생성 실패 | @SpringBootTest가 실제 DB 요구 | 테스트용 H2(PostgreSQL 모드) 프로파일 |
| 2 | Jackson 버전 충돌 우려 | Boot4=Jackson3, jjwt-jackson=Jackson2 | jjwt-gson + gson 사용 |
| 3 | 에러 details null 노출 | 기본 직렬화가 null 포함 | `default-property-inclusion=non_null` |
| 4 | subdomain NOT NULL INSERT 실패 | IDENTITY 즉시 INSERT | placeholder→`project-{id}` 갱신 |
| 5 | `@AutoConfigureMockMvc` 못 찾음 | Boot4 패키지 이동 | `…webmvc.test.autoconfigure`로 임포트 |
| 6 | Graphite 연결 경고 | 로컬에 graphite 없음 | export 비활성화 |
| 7 | Kotlin→Java 전환 | 팀 합의 언어 Java | 플러그인/소스/.gitignore 정리 |

---

## 10. 검증 결과

```
./gradlew clean build  →  BUILD SUCCESSFUL  (28 tests)
```
- 단위(상태머신) + 통합(API) 테스트 H2로 전부 통과(외부 의존 0 → CI 친화).
- 추가로 **실제 Postgres(Docker)** 기동 후 회원가입→로그인→프로젝트 생성/조회/목록 + 인증/중복 에러까지 엔드투엔드 수동 검증 완료(7개 테이블 자동 생성 확인).

---

## 11. 신규/변경 파일 목록 (총 40여 개)

```
[설정]   build.gradle(변경), application.properties(변경), 
         test/resources/application.properties(신규)
[진입점] AutodeployApplication.java (Kotlin→Java)
[enum]   DeploymentStatus, DeploymentTriggerType, LogLevel,
         RuntimeColor, RuntimeStatus, UserStatus, ProjectStatus, BuildType (8)
[엔티티] User, Project, EnvironmentVariable, Deployment, DeploymentLog,
         RuntimeInstance, Webhook (7)
[repo]   User/Project/EnvironmentVariable/Deployment/DeploymentLog/
         RuntimeInstance/Webhook Repository (7)
[global] ApiResponse, PagedResponse, ErrorCode, ApiException, ErrorResponse,
         GlobalExceptionHandler, JwtProperties, JwtTokenProvider,
         JwtAuthenticationFilter, AuthPrincipal, SecurityConfig (11)
[auth]   AuthController, AuthService, dto 5 (7)
[project]ProjectController, ProjectService, dto 5 (7)
[test]   DeploymentStatusTest, ApiIntegrationTest (2)
```
