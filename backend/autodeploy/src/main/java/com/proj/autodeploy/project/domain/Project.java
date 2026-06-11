package com.proj.autodeploy.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_owner_id", columnList = "owner_id"),
                @Index(name = "uk_projects_subdomain", columnList = "subdomain", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;

    @Column(name = "root_directory", length = 255)
    private String rootDirectory;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_type", nullable = false, length = 20)
    private BuildType buildType;

    @Column(name = "health_check_path", length = 200)
    private String healthCheckPath;

    @Column(name = "health_check_port")
    private Integer healthCheckPort;

    @Column(name = "health_check_timeout_seconds")
    private Integer healthCheckTimeoutSeconds;

    @Column(name = "health_check_interval_seconds")
    private Integer healthCheckIntervalSeconds;

    @Column(nullable = false, unique = true, length = 63)
    private String subdomain;

    @Column(name = "webhook_secret", nullable = false, length = 255)
    private String webhookSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Project(Long ownerId, String name, String description, String repositoryUrl,
                    String defaultBranch, String rootDirectory, BuildType buildType,
                    String healthCheckPath, Integer healthCheckPort,
                    Integer healthCheckTimeoutSeconds, Integer healthCheckIntervalSeconds,
                    String subdomain, String webhookSecret, ProjectStatus status) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.repositoryUrl = repositoryUrl;
        this.defaultBranch = defaultBranch != null ? defaultBranch : "main";
        this.rootDirectory = rootDirectory != null ? rootDirectory : "/";
        this.buildType = buildType != null ? buildType : BuildType.DOCKERFILE;
        this.healthCheckPath = healthCheckPath != null ? healthCheckPath : "/health";
        this.healthCheckPort = healthCheckPort != null ? healthCheckPort : 8080;
        this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds != null ? healthCheckTimeoutSeconds : 60;
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds != null ? healthCheckIntervalSeconds : 5;
        this.subdomain = subdomain;
        this.webhookSecret = webhookSecret;
        this.status = status != null ? status : ProjectStatus.ACTIVE;
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId != null && this.ownerId.equals(userId);
    }

    public void assignSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    /** PATCH: null 이 아닌 필드만 부분 수정. repositoryUrl / subdomain 은 변경 불가. */
    public void patch(String description, String defaultBranch, String rootDirectory,
                      String healthCheckPath, Integer healthCheckPort,
                      Integer healthCheckTimeoutSeconds, Integer healthCheckIntervalSeconds,
                      String name) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (defaultBranch != null) this.defaultBranch = defaultBranch;
        if (rootDirectory != null) this.rootDirectory = rootDirectory;
        if (healthCheckPath != null) this.healthCheckPath = healthCheckPath;
        if (healthCheckPort != null) this.healthCheckPort = healthCheckPort;
        if (healthCheckTimeoutSeconds != null) this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds;
        if (healthCheckIntervalSeconds != null) this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
    }
}
