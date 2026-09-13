package com.proj.autodeploy.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DeploymentLifecycleServiceTest {

    @Autowired DeploymentLifecycleService lifecycleService;
    @Autowired DeploymentRepository deploymentRepository;

    @Test
    @DisplayName("QUEUED 배포는 CLONING 으로 전이되고 시작 시각이 기록된다")
    void startProcessingFromQueued() {
        Long id = saveQueuedDeployment();

        boolean started = lifecycleService.startProcessing(id);

        assertThat(started).isTrue();
        Deployment found = deploymentRepository.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(DeploymentStatus.CLONING);
        assertThat(found.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("멱등성 — 같은 메시지를 두 번 받아도 두 번째는 false 이고 상태가 그대로다")
    void startProcessingIsIdempotent() {
        Long id = saveQueuedDeployment();
        lifecycleService.startProcessing(id);

        boolean secondCall = lifecycleService.startProcessing(id);

        assertThat(secondCall).isFalse();
        assertThat(deploymentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(DeploymentStatus.CLONING);
    }

    @Test
    @DisplayName("존재하지 않는 deploymentId 는 예외 없이 false 를 반환한다")
    void startProcessingMissingDeployment() {
        assertThat(lifecycleService.startProcessing(999_999L)).isFalse();
    }

    @Test
    @DisplayName("CLONING 배포는 FAILED 로 전이되고 실패 사유가 남는다")
    void markFailedFromCloning() {
        Long id = saveQueuedDeployment();
        lifecycleService.startProcessing(id);

        lifecycleService.markFailed(id, "docker build exited with 1");

        Deployment found = deploymentRepository.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(found.getFailureReason()).isEqualTo("docker build exited with 1");
        assertThat(found.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("QUEUED 에서는 FAILED 로 갈 수 없으므로 상태가 유지된다 (상태머신 가드)")
    void markFailedFromQueuedIsIgnored() {
        Long id = saveQueuedDeployment();

        lifecycleService.markFailed(id, "broker down");

        assertThat(deploymentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(DeploymentStatus.QUEUED);
    }

    private Long saveQueuedDeployment() {
        Deployment deployment = Deployment.builder()
                .projectId(1L)
                .branch("main")
                .build();
        deployment.transitionTo(DeploymentStatus.QUEUED);
        return deploymentRepository.save(deployment).getId();
    }
}
