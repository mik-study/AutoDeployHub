package com.proj.autodeploy.deployment.pipeline;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JDK {@link HttpClient} 기반 health 확인. 추가 의존성이 필요 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.pipeline.mode", havingValue = "real", matchIfMissing = true)
public class HttpHealthChecker implements HealthChecker {

    /** 한 번의 확인에 거는 시간. 폴링 간격보다 짧아야 다음 시도가 밀리지 않는다. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            // 컨테이너가 뜨는 중이면 리다이렉트 대신 그냥 실패로 보고 재시도하는 게 단순하다.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public boolean isHealthy(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status >= 200 && status < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // 컨테이너가 아직 준비되지 않은 정상적인 상황이 대부분이라 debug 로만 남긴다.
            log.debug("health check failed for {}: {}", url, e.toString());
            return false;
        }
    }
}
