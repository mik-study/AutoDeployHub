package com.proj.autodeploy.runtime.domain;

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

/**
 * 실제로 돌고 있는 컨테이너. Blue-Green 의 color/port/isActive 를 보유한다.
 * (03_erd.md §2.6)
 */
@Entity
@Table(
        name = "runtime_instances",
        indexes = {
                @Index(name = "uk_runtime_container_name", columnList = "container_name", unique = true),
                @Index(name = "idx_runtime_project_active", columnList = "project_id, is_active")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuntimeInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "deployment_id", nullable = false)
    private Long deploymentId;

    @Column(name = "container_name", nullable = false, unique = true, length = 100)
    private String containerName;

    @Column(name = "image_tag", nullable = false, length = 255)
    private String imageTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RuntimeColor color;

    @Column(nullable = false)
    private int port;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuntimeStatus status;

    @CreationTimestamp
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Builder
    private RuntimeInstance(Long projectId, Long deploymentId, String containerName, String imageTag,
                           RuntimeColor color, int port, boolean active, RuntimeStatus status) {
        this.projectId = projectId;
        this.deploymentId = deploymentId;
        this.containerName = containerName;
        this.imageTag = imageTag;
        this.color = color;
        this.port = port;
        this.active = active;
        this.status = status != null ? status : RuntimeStatus.STARTING;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changeStatus(RuntimeStatus status) {
        this.status = status;
        if (status == RuntimeStatus.STOPPED || status == RuntimeStatus.FAILED) {
            this.stoppedAt = Instant.now();
        }
    }
}
