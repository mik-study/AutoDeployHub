# Staged 파일 코드 전문 & 설명 (4주차 과제1 — Deployment API)

> 현재 git **staged** 상태인 파일 10개의 전체 코드와 간단 설명.
>
> - 작성일: 2026-06-21
> - 담당: 김현수
> - 범위: 4주차 과제 1번 (Deployment API, `05_api_spec.md §4.1~4.4`)
> - 빌드: `./gradlew clean build` → ✅ BUILD SUCCESSFUL

---

## Staged 파일 목록

| 상태 | 파일 |
|---|---|
| M | `project/ProjectService.java` (getOwnedProject 공개) |
| M | `deployment/DeploymentRepository.java` (중복 가드 메서드 추가) |
| A | `deployment/DeploymentController.java` |
| A | `deployment/DeploymentService.java` |
| A | `deployment/DeploymentPublisher.java` (seam 인터페이스) |
| A | `deployment/LoggingDeploymentPublisher.java` (스텁) |
| A | `deployment/dto/CreateDeploymentRequest.java` |
| A | `deployment/dto/DeploymentDetailResponse.java` |
| A | `deployment/dto/DeploymentSummaryResponse.java` |
| A | `test/api/DeploymentApiTest.java` |

> `docker-compose.yml`(M, postgres 포트 5433)과 본 보고서/week4 가이드 md는 **unstaged**라 제외.

---

## 1. `deployment/DeploymentRepository.java` (M)

**설명**: 이력 조회용 `findByProjectId` 는 기존. 이번에 **중복 배포 가드용** `existsByProjectIdAndStatusIn` 만 추가. (status IN (진행중 집합) 존재 여부)

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Page<Deployment> findByProjectId(Long projectId, Pageable pageable);

    /** 프로젝트에 진행 중(in-progress) 배포가 있는지. (중복 배포 가드용) */
    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<DeploymentStatus> statuses);
}
```

---

## 2. `deployment/DeploymentPublisher.java` (A)

**설명**: 배포 작업 **발행 seam 인터페이스**. 과제 1은 로그 스텁, 과제 2에서 RabbitMQ 구현체로 교체(서비스 수정 없이).

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;

/**
 * 배포 작업 발행 seam.
 *
 * <p>과제 1에서는 로그만 남기는 스텁({@link LoggingDeploymentPublisher})을 사용하고,
 * 과제 2에서 RabbitMQ(RabbitTemplate) 기반 구현으로 교체한다. (서비스 코드 수정 없이 구현체만 교체)
 */
public interface DeploymentPublisher {

    void publish(Deployment deployment);
}
```

## 3. `deployment/LoggingDeploymentPublisher.java` (A)

**설명**: 과제 1용 **스텁 구현**. 실제 큐 발행 대신 로그만 남긴다.

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 과제 1용 스텁 publisher. 실제 큐 발행 대신 로그만 남긴다.
 * 과제 2에서 RabbitMQ 구현체로 교체된다.
 */
@Slf4j
@Component
public class LoggingDeploymentPublisher implements DeploymentPublisher {

    @Override
    public void publish(Deployment deployment) {
        log.info("[STUB] publish to deploy.queue - deploymentId={}, projectId={}, branch={}",
                deployment.getId(), deployment.getProjectId(), deployment.getBranch());
    }
}
```

---

## 4. DTO 3종 (A)

### 4.1 `dto/CreateDeploymentRequest.java`

**설명**: 배포 요청 body. `branch`/`commitHash` 둘 다 nullable(미지정 시 기본 branch / HEAD).

```java
package com.proj.autodeploy.deployment.dto;

/**
 * 배포 요청. (05_api_spec.md §4.1)
 * branch 미지정 시 프로젝트의 defaultBranch 사용, commitHash 미지정 시 해당 branch HEAD(과제 3 Worker에서 결정).
 * 둘 다 nullable.
 */
public record CreateDeploymentRequest(
        String branch,
        String commitHash
) {
}
```

### 4.2 `dto/DeploymentDetailResponse.java`

**설명**: 단건 상세(§4.3). 생성(§4.1)·취소(§4.4) 응답에도 재사용(미설정 필드는 null → `non_null` 직렬화로 생략). `from(Deployment)` 정적 매핑.

```java
package com.proj.autodeploy.deployment.dto;

import com.proj.autodeploy.deployment.domain.Deployment;
import java.time.Instant;

/**
 * 배포 단건 상세. (05_api_spec.md §4.3)
 * 생성(§4.1)·취소(§4.4) 응답에도 재사용한다(아직 안 채워진 필드는 null → non_null 직렬화로 생략).
 */
public record DeploymentDetailResponse(
        Long deploymentId,
        Long projectId,
        Long previousDeploymentId,
        String branch,
        String commitHash,
        String commitMessage,
        String imageRepository,
        String imageTag,
        String status,
        String triggerType,
        String failureReason,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {

    public static DeploymentDetailResponse from(Deployment d) {
        return new DeploymentDetailResponse(
                d.getId(),
                d.getProjectId(),
                d.getPreviousDeploymentId(),
                d.getBranch(),
                d.getCommitHash(),
                d.getCommitMessage(),
                d.getImageRepository(),
                d.getImageTag(),
                d.getStatus().name(),
                d.getTriggerType().name(),
                d.getFailureReason(),
                d.getStartedAt(),
                d.getFinishedAt(),
                d.getCreatedAt()
        );
    }
}
```

### 4.3 `dto/DeploymentSummaryResponse.java`

**설명**: 이력 목록 행(§4.2). 상세보다 가벼운 필드 집합.

```java
package com.proj.autodeploy.deployment.dto;

import com.proj.autodeploy.deployment.domain.Deployment;
import java.time.Instant;

/**
 * 배포 이력 목록 행. (05_api_spec.md §4.2)
 */
public record DeploymentSummaryResponse(
        Long deploymentId,
        String branch,
        String commitHash,
        String commitMessage,
        String status,
        String triggerType,
        Instant startedAt,
        Instant finishedAt
) {

    public static DeploymentSummaryResponse from(Deployment d) {
        return new DeploymentSummaryResponse(
                d.getId(),
                d.getBranch(),
                d.getCommitHash(),
                d.getCommitMessage(),
                d.getStatus().name(),
                d.getTriggerType().name(),
                d.getStartedAt(),
                d.getFinishedAt()
        );
    }
}
```

---

## 5. `deployment/DeploymentService.java` (A)

**설명**: 핵심 비즈니스 로직.
- `IN_PROGRESS_STATUSES`: `DeploymentStatus.isInProgress()` 기준으로 한 번만 산출한 "진행 중" 집합(상태머신 미변경).
- `create`: 소유권 검증 → 중복 가드(409) → branch 기본값 → `PENDING→QUEUED` → save → 발행 seam.
- `list/get/cancel`: 소유권 검증 재사용. cancel 은 PENDING/QUEUED 외 422.
- `getOwnedDeployment`: 배포 로드 후 **소속 프로젝트로** 소유권 검증.

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.DeploymentTriggerType;
import com.proj.autodeploy.deployment.dto.CreateDeploymentRequest;
import com.proj.autodeploy.deployment.dto.DeploymentDetailResponse;
import com.proj.autodeploy.deployment.dto.DeploymentSummaryResponse;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.ProjectService;
import com.proj.autodeploy.project.domain.Project;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    /** 중복 배포 가드용 "진행 중" 상태 집합 (DeploymentStatus.isInProgress() 기준, 한 곳에서 산출). */
    private static final List<DeploymentStatus> IN_PROGRESS_STATUSES =
            Arrays.stream(DeploymentStatus.values())
                    .filter(DeploymentStatus::isInProgress)
                    .toList();

    private final DeploymentRepository deploymentRepository;
    private final ProjectService projectService;
    private final DeploymentPublisher deploymentPublisher;

    /**
     * 배포 요청. (05_api_spec.md §4.1) PENDING → QUEUED 후 발행 seam 호출.
     */
    @Transactional
    public DeploymentDetailResponse create(Long userId, Long projectId, CreateDeploymentRequest request) {
        Project project = projectService.getOwnedProject(userId, projectId);

        // 중복 배포 가드. TODO(과제 5): Redis 분산락으로 교체
        if (deploymentRepository.existsByProjectIdAndStatusIn(projectId, IN_PROGRESS_STATUSES)) {
            throw new ApiException(ErrorCode.DEPLOYMENT_ALREADY_IN_PROGRESS);
        }

        String branch = (request.branch() != null && !request.branch().isBlank())
                ? request.branch().trim()
                : project.getDefaultBranch();

        Deployment deployment = Deployment.builder()
                .projectId(projectId)
                .branch(branch)
                .commitHash(request.commitHash())
                .triggerType(DeploymentTriggerType.MANUAL)
                .build();                              // status = PENDING
        deployment.transitionTo(DeploymentStatus.QUEUED);
        deployment = deploymentRepository.save(deployment);

        // TODO(과제 2): 트랜잭션 커밋 후 발행(@TransactionalEventListener)으로 다듬기. 현재는 로그 스텁.
        deploymentPublisher.publish(deployment);

        return DeploymentDetailResponse.from(deployment);
    }

    /** 배포 이력. (05_api_spec.md §4.2) */
    @Transactional(readOnly = true)
    public Page<DeploymentSummaryResponse> list(Long userId, Long projectId, Pageable pageable) {
        projectService.getOwnedProject(userId, projectId); // 소유권 검증
        return deploymentRepository.findByProjectId(projectId, pageable)
                .map(DeploymentSummaryResponse::from);
    }

    /** 배포 단건 조회. (05_api_spec.md §4.3) */
    @Transactional(readOnly = true)
    public DeploymentDetailResponse get(Long userId, Long deploymentId) {
        return DeploymentDetailResponse.from(getOwnedDeployment(userId, deploymentId));
    }

    /** 배포 취소. (05_api_spec.md §4.4) PENDING/QUEUED 에서만 가능. */
    @Transactional
    public DeploymentDetailResponse cancel(Long userId, Long deploymentId) {
        Deployment deployment = getOwnedDeployment(userId, deploymentId);
        DeploymentStatus status = deployment.getStatus();
        // 명세 에러코드(422)를 정확히 던지기 위해 transitionTo 예외에 의존하지 않고 먼저 명시적으로 체크.
        if (status != DeploymentStatus.PENDING && status != DeploymentStatus.QUEUED) {
            throw new ApiException(ErrorCode.DEPLOYMENT_NOT_CANCELABLE);
        }
        deployment.transitionTo(DeploymentStatus.CANCELED);
        return DeploymentDetailResponse.from(deployment);
    }

    /** deployment 로드 + (소속 프로젝트 기준) 소유권 검증. */
    private Deployment getOwnedDeployment(Long userId, Long deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.DEPLOYMENT_NOT_FOUND, "deploymentId", deploymentId));
        projectService.getOwnedProject(userId, deployment.getProjectId());
        return deployment;
    }
}
```

---

## 6. `deployment/DeploymentController.java` (A)

**설명**: 4개 엔드포인트. 경로가 `/api/projects/{id}/deployments` 와 `/api/deployments/{id}` 두 갈래라 클래스 레벨 `@RequestMapping` 없이 메서드별 전체 경로. 이력은 `createdAt DESC` 기본 정렬.

```java
package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.dto.CreateDeploymentRequest;
import com.proj.autodeploy.deployment.dto.DeploymentDetailResponse;
import com.proj.autodeploy.deployment.dto.DeploymentSummaryResponse;
import com.proj.autodeploy.global.response.ApiResponse;
import com.proj.autodeploy.global.response.PagedResponse;
import com.proj.autodeploy.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deployment API. (05_api_spec.md §4.1~4.4)
 * 경로가 /api/projects/{id}/deployments 와 /api/deployments/{id} 두 갈래라 메서드별 전체 경로를 둔다.
 */
@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    /** §4.1 배포 요청 → 202 (QUEUED). body 는 선택(없으면 defaultBranch). */
    @PostMapping("/api/projects/{projectId}/deployments")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @RequestBody(required = false) CreateDeploymentRequest request) {
        CreateDeploymentRequest req = (request != null) ? request : new CreateDeploymentRequest(null, null);
        DeploymentDetailResponse response = deploymentService.create(principal.userId(), projectId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(response));
    }

    /** §4.2 배포 이력 (최신순). */
    @GetMapping("/api/projects/{projectId}/deployments")
    public ResponseEntity<PagedResponse<DeploymentSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DeploymentSummaryResponse> page = deploymentService.list(principal.userId(), projectId, pageable);
        return ResponseEntity.ok(PagedResponse.of(page));
    }

    /** §4.3 배포 단건 조회. */
    @GetMapping("/api/deployments/{deploymentId}")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId) {
        return ResponseEntity.ok(ApiResponse.of(deploymentService.get(principal.userId(), deploymentId)));
    }

    /** §4.4 배포 취소 (PENDING/QUEUED 만). */
    @PostMapping("/api/deployments/{deploymentId}/cancel")
    public ResponseEntity<ApiResponse<DeploymentDetailResponse>> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long deploymentId) {
        return ResponseEntity.ok(ApiResponse.of(deploymentService.cancel(principal.userId(), deploymentId)));
    }
}
```

---

## 7. `project/ProjectService.java` (M)

**설명**: 이번 staged 변경의 핵심은 **`getOwnedProject` 를 private → public** 으로 노출(맨 아래). DeploymentService 가 소유권 검증을 재사용하기 위함. 나머지 로직은 기존과 동일.
> 참고: 상단 import 중 `JwtTokenProvider`/`UserRepository`/`PasswordEncoder` 는 현재 미사용 import(경고) — 다음 정리 때 제거 권장.

```java
package com.proj.autodeploy.project;

import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.project.domain.ProjectStatus;
import com.proj.autodeploy.project.dto.CreateProjectRequest;
import com.proj.autodeploy.project.dto.CreateProjectResponse;
import com.proj.autodeploy.project.dto.ProjectResponse;
import com.proj.autodeploy.project.dto.ProjectSummaryResponse;
import com.proj.autodeploy.project.dto.UpdateProjectRequest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import com.proj.autodeploy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    // MVP: 단일 호스트의 webhook 수신 endpoint. (운영 도메인 확정 시 설정으로 분리)
    private static final String WEBHOOK_URL = "https://api.autodeploy.dev/api/webhooks/github";

    private final ProjectRepository projectRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreateProjectResponse create(Long userId, CreateProjectRequest request) {
        String requestedSubdomain = normalize(request.subdomain());
        if (requestedSubdomain != null && projectRepository.existsBySubdomain(requestedSubdomain)) {
            throw ApiException.of(ErrorCode.PROJECT_SUBDOMAIN_DUPLICATED, "subdomain", requestedSubdomain);
        }

        String plainSecret = generateWebhookSecret();

        // subdomain 은 NOT NULL + UNIQUE. IDENTITY 전략이라 save() 시 즉시 INSERT 되므로,
        // 미지정 시 임시 placeholder 로 INSERT 한 뒤 발급된 id 로 project-{id} 로 갱신한다.
        boolean autoGenerate = (requestedSubdomain == null);
        String initialSubdomain = autoGenerate
                ? "pending-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : requestedSubdomain;

        Project project = Project.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .repositoryUrl(request.repositoryUrl())
                .defaultBranch(request.defaultBranch())
                .rootDirectory(request.rootDirectory())
                .healthCheckPath(request.healthCheckPath())
                .healthCheckPort(request.healthCheckPort())
                .healthCheckTimeoutSeconds(request.healthCheckTimeoutSeconds())
                .healthCheckIntervalSeconds(request.healthCheckIntervalSeconds())
                .subdomain(initialSubdomain)
                .webhookSecret(plainSecret)    // TODO(4주차): 암호화 저장
                .build();

        project = projectRepository.save(project);

        if (autoGenerate) {
            project.assignSubdomain("project-" + project.getId());
        }

        return CreateProjectResponse.from(project, WEBHOOK_URL, plainSecret);
    }

    // TODO : 트랜잭션 애노테이션 빼는걸로 검토
    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> list(Long userId, Pageable pageable) {
        return projectRepository.findByOwnerIdAndStatus(userId, ProjectStatus.ACTIVE, pageable)
                .map(ProjectSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        return ProjectResponse.from(getOwnedProject(userId, projectId));
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, UpdateProjectRequest request) {
        Project project = getOwnedProject(userId, projectId);
        project.patch(
                request.description(),
                request.defaultBranch(),
                request.rootDirectory(),
                request.healthCheckPath(),
                request.healthCheckPort(),
                request.healthCheckTimeoutSeconds(),
                request.healthCheckIntervalSeconds(),
                request.name()
        );
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.archive(); // soft delete
    }

    /**
     * 프로젝트 조회 + 소유권 검증. 다른 도메인 서비스(예: deployment)에서 재사용하도록 public.
     *
     * @throws ApiException PROJECT_NOT_FOUND(404) / PROJECT_ACCESS_DENIED(403)
     */
    @Transactional(readOnly = true)
    public Project getOwnedProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PROJECT_NOT_FOUND, "projectId", projectId));
        if (!project.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return project;
    }

    private String normalize(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            return null;
        }
        return subdomain.trim();
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "whs_" + HexFormat.of().formatHex(bytes);
    }
}
```

---

## 8. `test/api/DeploymentApiTest.java` (A)

**설명**: H2 통합 테스트 2개. ① 생성(202,QUEUED)→재요청(409)→이력(1건)→단건→취소(200,CANCELED) E2E, ② 이미 취소된 배포 재취소 시 422.

```java
package com.proj.autodeploy.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.user.UserRepository;
import com.proj.autodeploy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deployment API 통합 테스트 (H2). 과제 1 완료 판정. (가이드 §7)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeploymentApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    @DisplayName("배포 생성 → 중복 409 → 이력 → 단건 → 취소 E2E")
    void deploymentLifecycle() throws Exception {
        String token = tokenForNewUser("deployer@test.com");
        String auth = "Bearer " + token;
        long projectId = createProject(auth);

        // 1) 배포 요청 → 202, QUEUED
        String createBody = mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.deploymentId").exists())
                .andExpect(jsonPath("$.data.projectId").value((int) projectId))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        long deploymentId = ((Number) JsonPath.read(createBody, "$.data.deploymentId")).longValue();

        // 2) 같은 프로젝트 재요청 → 409 (진행 중 배포 존재)
        mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_ALREADY_IN_PROGRESS"));

        // 3) 이력 → 1건
        mockMvc.perform(get("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].status").value("QUEUED"));

        // 4) 단건 조회
        mockMvc.perform(get("/api/deployments/{id}", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deploymentId").value((int) deploymentId))
                .andExpect(jsonPath("$.data.projectId").value((int) projectId));

        // 5) 취소 → 200, CANCELED
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("진행 단계가 아닌 배포 취소 시 422")
    void cancelNonCancelable() throws Exception {
        String token = tokenForNewUser("deployer2@test.com");
        String auth = "Bearer " + token;
        long projectId = createProject(auth);

        String body = mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long deploymentId = ((Number) JsonPath.read(body, "$.data.deploymentId")).longValue();

        // 첫 취소 → CANCELED
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        // 이미 CANCELED(취소 불가 상태) → 422
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_NOT_CANCELABLE"));
    }

    private long createProject(String auth) throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"demo","repositoryUrl":"https://github.com/myorg/demo"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.projectId")).longValue();
    }

    private String tokenForNewUser(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("P@ssw0rd!"))
                .name("owner")
                .build());
        return tokenProvider.createAccessToken(user.getId(), user.getEmail());
    }
}
```

---

## 흐름 요약

```
POST /api/projects/{id}/deployments
 → Controller.create → Service.create
    → projectService.getOwnedProject (소유권)
    → existsByProjectIdAndStatusIn (중복 409)
    → Deployment(PENDING) → transitionTo(QUEUED) → save
    → deploymentPublisher.publish (로그 스텁; 과제2에서 RabbitMQ)
 → 202 { data: DeploymentDetailResponse(status=QUEUED) }
```
