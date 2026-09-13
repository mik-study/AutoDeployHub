package com.proj.autodeploy.deployment.pipeline;

/**
 * 배포된 컨테이너의 health 엔드포인트 확인. (과제 3 §4.2)
 *
 * <p>폴링 루프(간격·타임아웃)는 호출부인 Worker 가 돌린다. 여기서는 <b>한 번의 확인</b>만 책임진다 —
 * 재시도 정책이 구현체마다 달라지면 mock 과 실제의 동작이 갈린다.
 */
public interface HealthChecker {

    /**
     * @param url 확인할 절대 URL (예: {@code http://autodeploy-p1-d3-blue:8080/health})
     * @return 2xx 응답이면 true. 연결 실패·타임아웃·5xx 는 false (예외를 던지지 않는다 —
     *         컨테이너가 아직 뜨는 중이면 실패가 정상이고, 폴링으로 재시도해야 하기 때문)
     */
    boolean isHealthy(String url);
}
