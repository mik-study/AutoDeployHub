package com.proj.autodeploy.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.pipeline.CommandRunner;
import com.proj.autodeploy.project.ProjectRepository;
import com.proj.autodeploy.project.domain.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Worker 파이프라인 실패 분기. (과제 3)
 *
 * <p>{@link CommandRunner} 를 mock 으로 갈아끼워 외부 명령이 실패하도록 만든다.
 *
 * <p>Worker 는 <b>예외를 던지기만</b> 하고 {@code FAILED} 전이는 호출부
 * ({@code DeployQueueListener})가 한다 — 과제 2에서 확정한 계약이다.
 */
@SpringBootTest
class DeploymentWorkerFailureTest {

    @MockitoBean CommandRunner commandRunner;

    @Autowired DeploymentWorker worker;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentLogRepository deploymentLogRepository;
    @Autowired ProjectRepository projectRepository;

    @Test
    @DisplayName("외부 명령이 0 이 아닌 코드로 끝나면 예외가 전파되고 실패 사유가 로그에 남는다")
    void propagatesFailureAndLogsReason() {
        when(commandRunner.run(any(), anyList(), any(), any())).thenReturn(1);
        Long deploymentId = givenCloningDeployment();

        assertThatThrownBy(() -> worker.run(deploymentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git clone");

        assertThat(logMessages(deploymentId)).contains("배포 실패");
    }

    @Test
    @DisplayName("Worker 는 FAILED 로 바꾸지 않는다 — 상태 정리는 호출부(컨슈머) 책임")
    void doesNotTransitionToFailedItself() {
        when(commandRunner.run(any(), anyList(), any(), any())).thenReturn(1);
        Long deploymentId = givenCloningDeployment();

        assertThatThrownBy(() -> worker.run(deploymentId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(deploymentRepository.findById(deploymentId).orElseThrow().getStatus())
                .isEqualTo(DeploymentStatus.CLONING);
    }

    @Test
    @DisplayName("명령이 예외를 던져도 로그를 남기고 그대로 전파한다")
    void propagatesRuntimeException() {
        when(commandRunner.run(any(), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("docker daemon not reachable"));
        Long deploymentId = givenCloningDeployment();

        assertThatThrownBy(() -> worker.run(deploymentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker daemon");

        assertThat(logMessages(deploymentId)).contains("docker daemon not reachable");
    }

    // ------------------------------------------------------------------

    private String logMessages(Long deploymentId) {
        return String.join("\n", deploymentLogRepository
                .findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(deploymentId, 0L)
                .stream().map(l -> l.getMessage()).toList());
    }

    private Long givenCloningDeployment() {
        Project project = projectRepository.save(Project.builder()
                .ownerId(1L)
                .name("worker-fail")
                .repositoryUrl("https://github.com/myorg/demo")
                .defaultBranch("main")
                .subdomain("fail-" + System.nanoTime())
                .webhookSecret("secret")
                .healthCheckTimeoutSeconds(5)
                .healthCheckIntervalSeconds(1)
                .build());

        Deployment deployment = Deployment.builder()
                .projectId(project.getId())
                .branch("main")
                .build();
        deployment.transitionTo(DeploymentStatus.QUEUED);
        deployment.transitionTo(DeploymentStatus.CLONING);
        deployment.markStarted();
        return deploymentRepository.save(deployment).getId();
    }
}
