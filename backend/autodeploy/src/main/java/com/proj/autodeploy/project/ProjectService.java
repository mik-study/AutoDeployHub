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

    private Project getOwnedProject(Long userId, Long projectId) {
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
