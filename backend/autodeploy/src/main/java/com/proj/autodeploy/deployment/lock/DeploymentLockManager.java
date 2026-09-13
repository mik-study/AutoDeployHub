package com.proj.autodeploy.deployment.lock;

/**
 * 프로젝트별 배포 동시성 제어. (과제 5)
 *
 * <p>한 프로젝트에 진행 중 배포가 있으면 새 배포를 막는다. 과제 1에서 임시로 두었던
 * {@code existsByProjectIdAndStatusIn} DB 가드를 이 락이 대체한다.
 *
 * <p>인터페이스로 둔 이유는 테스트 때문이다. 통합 테스트가 Redis 브로커를 요구하면 CI 가 무거워지므로,
 * 테스트 프로파일에서는 {@link InMemoryDeploymentLockManager} 로 갈아끼운다.
 */
public interface DeploymentLockManager {

    /**
     * 락 획득 시도. 원자적이어야 한다 (동시에 두 요청이 들어와도 하나만 true).
     *
     * @param projectId    락 대상 프로젝트
     * @param deploymentId 락 소유자. 값으로 저장돼 {@link #unlock} 에서 자기 락인지 확인하는 데 쓰인다
     * @return 획득했으면 true, 이미 누가 잡고 있으면 false
     */
    boolean tryLock(Long projectId, Long deploymentId);

    /**
     * 락 해제. <b>자신이 잡은 락일 때만</b> 지운다.
     *
     * <p>토큰 확인이 없으면 이런 사고가 난다: A 배포가 TTL 만료로 락을 잃고, B 배포가 새로 락을 잡은
     * 직후에 A 가 뒤늦게 unlock 을 호출해 <b>B 의 락을 지운다.</b> 그러면 C 가 끼어들어 B 와 동시에
     * 배포가 돈다.
     *
     * @return 실제로 해제했으면 true, 락이 없거나 남의 락이면 false
     */
    boolean unlock(Long projectId, Long deploymentId);
}
