package com.proj.autodeploy.environment.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "environment_variables",
        indexes = {
                @Index(name = "uk_env_project_key", columnList = "project_id, env_key", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvironmentVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // 'key' 는 일부 DB 예약어이므로 컬럼명은 env_key 로 매핑
    @Column(name = "env_key", nullable = false, length = 100)
    private String key;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private EnvironmentVariable(Long projectId, String key, String encryptedValue, boolean secret) {
        this.projectId = projectId;
        this.key = key;
        this.encryptedValue = encryptedValue;
        this.secret = secret;
    }

    public void changeValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }
}
