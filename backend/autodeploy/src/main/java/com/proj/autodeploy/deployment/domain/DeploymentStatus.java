package com.proj.autodeploy.deployment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Deployment 상태머신. (04_state_machine.md)
 *
 * Blue-Green 무중단 배포 기준으로 SWITCHING_TRAFFIC 상태를 포함한다.
 * 상태 전이 규칙은 {@link #canTransitionTo(DeploymentStatus)} 로 강제한다.
 */
public enum DeploymentStatus {
    PENDING,
    QUEUED,
    CLONING,
    CHECKING_DOCKERFILE,
    BUILDING,
    PUSHING_IMAGE,
    DEPLOYING,
    HEALTH_CHECKING,
    SWITCHING_TRAFFIC,  // Blue-Green: 트래픽 전환
    SUCCEEDED,
    FAILED,
    CANCELED,
    ROLLING_BACK,
    ROLLED_BACK,
    ROLLBACK_FAILED;

    private static final Map<DeploymentStatus, Set<DeploymentStatus>> TRANSITIONS =
            new EnumMap<>(DeploymentStatus.class);

    static {
        TRANSITIONS.put(PENDING, EnumSet.of(QUEUED, FAILED, CANCELED));
        TRANSITIONS.put(QUEUED, EnumSet.of(CLONING, CANCELED));
        TRANSITIONS.put(CLONING, EnumSet.of(CHECKING_DOCKERFILE, FAILED));
        TRANSITIONS.put(CHECKING_DOCKERFILE, EnumSet.of(BUILDING, FAILED));
        TRANSITIONS.put(BUILDING, EnumSet.of(PUSHING_IMAGE, FAILED));
        TRANSITIONS.put(PUSHING_IMAGE, EnumSet.of(DEPLOYING, FAILED));
        TRANSITIONS.put(DEPLOYING, EnumSet.of(HEALTH_CHECKING, FAILED));
        TRANSITIONS.put(HEALTH_CHECKING, EnumSet.of(SWITCHING_TRAFFIC, FAILED));
        TRANSITIONS.put(SWITCHING_TRAFFIC, EnumSet.of(SUCCEEDED, FAILED));
        TRANSITIONS.put(SUCCEEDED, EnumSet.noneOf(DeploymentStatus.class));
        // FAILED 는 종착이지만, 사용자가 명시적 rollback 을 요청하면 ROLLING_BACK 으로만 전이 가능
        TRANSITIONS.put(FAILED, EnumSet.of(ROLLING_BACK));
        TRANSITIONS.put(CANCELED, EnumSet.noneOf(DeploymentStatus.class));
        TRANSITIONS.put(ROLLING_BACK, EnumSet.of(ROLLED_BACK, ROLLBACK_FAILED));
        TRANSITIONS.put(ROLLED_BACK, EnumSet.noneOf(DeploymentStatus.class));
        TRANSITIONS.put(ROLLBACK_FAILED, EnumSet.noneOf(DeploymentStatus.class));
    }

    /** 더 이상 진행하지 않는 종착 상태인가. */
    public boolean isTerminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELED
                || this == ROLLED_BACK
                || this == ROLLBACK_FAILED;
    }

    /** Worker 가 처리 중인(진행 중) 상태인가. (PENDING/종착 제외) */
    public boolean isInProgress() {
        return !isTerminal() && this != PENDING;
    }

    /** 현재 상태에서 next 로 전이가 허용되는가. */
    public boolean canTransitionTo(DeploymentStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(next);
    }

    /** 현재 상태에서 전이 가능한 다음 상태 집합. */
    public Set<DeploymentStatus> nextStates() {
        return Collections.unmodifiableSet(TRANSITIONS.getOrDefault(this, EnumSet.noneOf(DeploymentStatus.class)));
    }
}
