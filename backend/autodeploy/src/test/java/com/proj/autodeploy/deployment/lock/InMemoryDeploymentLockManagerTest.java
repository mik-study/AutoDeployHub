package com.proj.autodeploy.deployment.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 락 계약 단위 테스트.
 *
 * <p>Redis 구현({@link RedisDeploymentLockManager})은 브로커가 필요해 여기서 검증하지 않는다.
 * 대신 <b>두 구현이 같은 계약을 만족하도록</b> 이 테스트가 기준이 된다 —
 * {@code putIfAbsent}/{@code remove(k,v)} 가 {@code SET NX}/{@code compare-and-delete} 와
 * 같은 의미라는 게 요점이다.
 */
class InMemoryDeploymentLockManagerTest {

    private static final Long PROJECT = 12L;

    private final DeploymentLockManager lockManager = new InMemoryDeploymentLockManager();

    @Test
    @DisplayName("첫 획득은 성공하고, 같은 프로젝트의 두 번째 획득은 실패한다")
    void secondLockOnSameProjectFails() {
        assertThat(lockManager.tryLock(PROJECT, 1L)).isTrue();

        assertThat(lockManager.tryLock(PROJECT, 2L)).isFalse();
    }

    @Test
    @DisplayName("해제 후에는 다시 획득할 수 있다")
    void canReacquireAfterUnlock() {
        lockManager.tryLock(PROJECT, 1L);

        assertThat(lockManager.unlock(PROJECT, 1L)).isTrue();
        assertThat(lockManager.tryLock(PROJECT, 2L)).isTrue();
    }

    @Test
    @DisplayName("프로젝트가 다르면 서로 막지 않는다 (글로벌 락이 아니다)")
    void locksAreScopedPerProject() {
        assertThat(lockManager.tryLock(1L, 100L)).isTrue();

        assertThat(lockManager.tryLock(2L, 200L)).isTrue();
    }

    @Test
    @DisplayName("★ 남의 락은 해제할 수 없다 — TTL 만료 후 늦게 도착한 unlock 이 새 락을 지우는 사고 방지")
    void cannotUnlockSomeoneElsesLock() {
        lockManager.tryLock(PROJECT, 1L);   // 배포 1이 락을 쥠

        // 배포 2가 (예: TTL 만료 후 잡았다고 착각하고) 해제를 시도
        assertThat(lockManager.unlock(PROJECT, 2L)).isFalse();
        // 배포 1의 락은 그대로 살아 있어야 한다
        assertThat(lockManager.tryLock(PROJECT, 3L)).isFalse();
    }

    @Test
    @DisplayName("없는 락을 해제하면 false 를 반환하고 예외는 나지 않는다")
    void unlockingMissingLockIsSafe() {
        assertThat(lockManager.unlock(PROJECT, 1L)).isFalse();
    }

    @Test
    @DisplayName("동시에 여러 스레드가 달려들어도 정확히 하나만 획득한다")
    void onlyOneThreadAcquiresUnderContention() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();
        var winners = ConcurrentHashMap.<Long>newKeySet();

        try {
            for (long i = 0; i < threads; i++) {
                long deploymentId = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        if (lockManager.tryLock(PROJECT, deploymentId)) {
                            acquired.incrementAndGet();
                            winners.add(deploymentId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(acquired.get()).isEqualTo(1);
        // 승자만 해제할 수 있다
        assertThat(lockManager.unlock(PROJECT, winners.iterator().next())).isTrue();
    }
}
