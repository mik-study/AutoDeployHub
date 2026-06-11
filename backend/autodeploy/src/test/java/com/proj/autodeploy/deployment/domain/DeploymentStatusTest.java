package com.proj.autodeploy.deployment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Deployment 상태머신 단위 테스트. (04_state_machine.md §7)
 */
class DeploymentStatusTest {

    @Test
    @DisplayName("정상 흐름: PENDING → ... → SUCCEEDED 전이가 모두 허용된다")
    void happyPathTransitionsAreAllowed() {
        List<DeploymentStatus> happyPath = List.of(
                DeploymentStatus.PENDING,
                DeploymentStatus.QUEUED,
                DeploymentStatus.CLONING,
                DeploymentStatus.CHECKING_DOCKERFILE,
                DeploymentStatus.BUILDING,
                DeploymentStatus.PUSHING_IMAGE,
                DeploymentStatus.DEPLOYING,
                DeploymentStatus.HEALTH_CHECKING,
                DeploymentStatus.SWITCHING_TRAFFIC,
                DeploymentStatus.SUCCEEDED
        );
        for (int i = 0; i < happyPath.size() - 1; i++) {
            DeploymentStatus current = happyPath.get(i);
            DeploymentStatus next = happyPath.get(i + 1);
            assertThat(current.canTransitionTo(next))
                    .as("%s -> %s 는 허용되어야 함", current, next)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("진행 단계는 FAILED 로 전이할 수 있다")
    void inProgressStatesCanFail() {
        List<DeploymentStatus> canFail = List.of(
                DeploymentStatus.CLONING,
                DeploymentStatus.CHECKING_DOCKERFILE,
                DeploymentStatus.BUILDING,
                DeploymentStatus.PUSHING_IMAGE,
                DeploymentStatus.DEPLOYING,
                DeploymentStatus.HEALTH_CHECKING,
                DeploymentStatus.SWITCHING_TRAFFIC
        );
        for (DeploymentStatus status : canFail) {
            assertThat(status.canTransitionTo(DeploymentStatus.FAILED))
                    .as("%s -> FAILED 는 허용되어야 함", status)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("SUCCEEDED 는 어떤 상태로도 전이할 수 없다")
    void succeededIsTerminal() {
        for (DeploymentStatus next : DeploymentStatus.values()) {
            assertThat(DeploymentStatus.SUCCEEDED.canTransitionTo(next)).isFalse();
        }
        assertThat(DeploymentStatus.SUCCEEDED.nextStates()).isEmpty();
    }

    @Test
    @DisplayName("FAILED 는 ROLLING_BACK 으로만 전이할 수 있다(그 외 전이 거부)")
    void failedOnlyAllowsRollingBack() {
        assertThat(DeploymentStatus.FAILED.canTransitionTo(DeploymentStatus.ROLLING_BACK)).isTrue();
        for (DeploymentStatus next : DeploymentStatus.values()) {
            if (next != DeploymentStatus.ROLLING_BACK) {
                assertThat(DeploymentStatus.FAILED.canTransitionTo(next))
                        .as("FAILED -> %s 는 거부되어야 함", next)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("ROLLING_BACK 은 ROLLED_BACK / ROLLBACK_FAILED 로만 전이할 수 있다")
    void rollingBackTransitions() {
        assertThat(DeploymentStatus.ROLLING_BACK.canTransitionTo(DeploymentStatus.ROLLED_BACK)).isTrue();
        assertThat(DeploymentStatus.ROLLING_BACK.canTransitionTo(DeploymentStatus.ROLLBACK_FAILED)).isTrue();
        assertThat(DeploymentStatus.ROLLING_BACK.canTransitionTo(DeploymentStatus.SUCCEEDED)).isFalse();
        assertThat(DeploymentStatus.ROLLING_BACK.canTransitionTo(DeploymentStatus.QUEUED)).isFalse();
    }

    @Test
    @DisplayName("null 로의 전이는 항상 거부된다")
    void nullTransitionRejected() {
        assertThat(DeploymentStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Nested
    @DisplayName("isTerminal / isInProgress 정확성")
    class TerminalAndInProgress {

        @ParameterizedTest
        @EnumSource(value = DeploymentStatus.class,
                names = {"SUCCEEDED", "FAILED", "CANCELED", "ROLLED_BACK", "ROLLBACK_FAILED"})
        @DisplayName("종착 상태는 isTerminal=true, isInProgress=false")
        void terminalStates(DeploymentStatus status) {
            assertThat(status.isTerminal()).isTrue();
            assertThat(status.isInProgress()).isFalse();
        }

        @Test
        @DisplayName("PENDING 은 종착도 진행중도 아니다")
        void pendingIsNeither() {
            assertThat(DeploymentStatus.PENDING.isTerminal()).isFalse();
            assertThat(DeploymentStatus.PENDING.isInProgress()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = DeploymentStatus.class,
                names = {"QUEUED", "CLONING", "CHECKING_DOCKERFILE", "BUILDING", "PUSHING_IMAGE",
                        "DEPLOYING", "HEALTH_CHECKING", "SWITCHING_TRAFFIC", "ROLLING_BACK"})
        @DisplayName("진행 단계는 isInProgress=true, isTerminal=false")
        void inProgressStates(DeploymentStatus status) {
            assertThat(status.isInProgress()).isTrue();
            assertThat(status.isTerminal()).isFalse();
        }
    }
}
