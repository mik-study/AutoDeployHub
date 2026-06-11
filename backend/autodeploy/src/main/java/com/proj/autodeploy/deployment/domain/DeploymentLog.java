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
        name = "deployment_logs",
        indexes = {
                @Index(name = "idx_logs_deployment_seq", columnList = "deployment_id, sequence")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(nullable = false)
    private Long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LogLevel level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private DeploymentLog(Long deploymentId, Long sequence, LogLevel level, String message) {
        this.deploymentId = deploymentId;
        this.sequence = sequence;
        this.level = level != null ? level : LogLevel.INFO;
        this.message = message;
    }
}
