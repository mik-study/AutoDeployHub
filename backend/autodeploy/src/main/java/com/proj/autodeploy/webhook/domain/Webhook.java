package com.proj.autodeploy.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * GitHub Webhook 수신 이력. (03_erd.md §2.7, MVP 는 GitHub 만)
 */
@Entity
@Table(
        name = "webhooks",
        indexes = {
                @Index(name = "uk_webhooks_delivery_id", columnList = "delivery_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "delivery_id", unique = true, length = 100)
    private String deliveryId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(nullable = false)
    private boolean processed;

    @Column(name = "created_deployment_id")
    private Long createdDeploymentId;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Builder
    private Webhook(Long projectId, String eventType, String deliveryId, String payload,
                    boolean signatureValid, boolean processed, Long createdDeploymentId) {
        this.projectId = projectId;
        this.eventType = eventType;
        this.deliveryId = deliveryId;
        this.payload = payload;
        this.signatureValid = signatureValid;
        this.processed = processed;
        this.createdDeploymentId = createdDeploymentId;
    }

    public void markProcessed(Long createdDeploymentId) {
        this.processed = true;
        this.createdDeploymentId = createdDeploymentId;
    }
}
