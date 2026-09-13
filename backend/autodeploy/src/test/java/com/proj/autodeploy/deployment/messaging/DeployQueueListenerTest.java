package com.proj.autodeploy.deployment.messaging;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proj.autodeploy.deployment.DeploymentLifecycleService;
import com.proj.autodeploy.deployment.DeploymentWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeployQueueListenerTest {

    private static final DeploymentRequested MESSAGE =
            new DeploymentRequested(1L, 12L, "main", "abc1234");

    @Mock DeploymentLifecycleService lifecycleService;
    @Mock DeploymentWorker deploymentWorker;
    @InjectMocks DeployQueueListener listener;

    @Test
    @DisplayName("QUEUED 상태였으면 워커 파이프라인을 호출한다")
    void runsWorkerWhenTransitionSucceeds() {
        when(lifecycleService.startProcessing(1L)).thenReturn(true);

        listener.onDeploymentRequested(MESSAGE);

        verify(deploymentWorker).run(1L);
    }

    @Test
    @DisplayName("이미 처리됐거나 없는 배포면 워커를 호출하지 않는다 (멱등성)")
    void skipsWorkerWhenTransitionFails() {
        when(lifecycleService.startProcessing(1L)).thenReturn(false);

        listener.onDeploymentRequested(MESSAGE);

        verify(deploymentWorker, never()).run(1L);
    }

    @Test
    @DisplayName("파이프라인이 예외를 던지면 FAILED 로 기록하고 예외를 삼킨다 (무한 재큐잉 방지)")
    void marksFailedAndSwallowsException() {
        when(lifecycleService.startProcessing(1L)).thenReturn(true);
        doThrow(new IllegalStateException("docker daemon not reachable"))
                .when(deploymentWorker).run(1L);

        assertThatNoException().isThrownBy(() -> listener.onDeploymentRequested(MESSAGE));

        verify(lifecycleService).markFailed(eq(1L), contains("docker daemon"));
    }
}
