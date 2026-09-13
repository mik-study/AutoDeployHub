package com.proj.autodeploy.deployment.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * mock 파이프라인용 health 확인. 항상 통과한다.
 *
 * <p>{@link FakeCommandRunner} 가 실제 컨테이너를 띄우지 않으므로 붙을 대상이 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.pipeline.mode", havingValue = "mock")
public class AlwaysHealthyChecker implements HealthChecker {

    @Override
    public boolean isHealthy(String url) {
        log.info("[mock] health check passed: {}", url);
        return true;
    }
}
