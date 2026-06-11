package com.proj.autodeploy.deployment.domain;

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

@Entity
@Table(
        name = "deployments",
        indexes = {
                @Index(name = "idx_deployments_project_created", columnList = "project_id, created_at"),
                @Index(name = "idx_deployments_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "previous_deployment_id")
    private Long previousDeploymentId;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(name = "commit_hash", length = 40)
    private String commitHash;

    @Column(name = "commit_message", length = 500)
    private String commitMessage;

    @Column(name = "image_repository", length = 255)
    private String imageRepository;

    @Column(name = "image_tag", length = 255)
    private String imageTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeploymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private DeploymentTriggerType triggerType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Deployment(Long projectId, Long previousDeploymentId, String branch, String commitHash,
                       String commitMessage, String imageRepository, String imageTag,
                       DeploymentStatus status, DeploymentTriggerType triggerType) {
        this.projectId = projectId;
        this.previousDeploymentId = previousDeploymentId;
        this.branch = branch;
        this.commitHash = commitHash;
        this.commitMessage = commitMessage;
        this.imageRepository = imageRepository;
        this.imageTag = imageTag;
        this.status = status != null ? status : DeploymentStatus.PENDING;
        this.triggerType = triggerType != null ? triggerType : DeploymentTriggerType.MANUAL;
    }

    /**
     * 상태머신 규칙(04_state_machine.md)을 강제하며 상태를 전이한다.
     *
     * @throws IllegalStateException 허용되지 않은 전이일 때
     */
    public void transitionTo(DeploymentStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Illegal deployment status transition: " + this.status + " -> " + next);
        }
        this.status = next;
        if (next.isTerminal()) {
            this.finishedAt = Instant.now();
        }
    }

    public void markStarted() {
        this.startedAt = Instant.now();
    }

    public void markFailureReason(String reason) {
        this.failureReason = reason;
    }
}
