package com.proj.autodeploy.deployment.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 브로커 없이 도는 락 구현. {@code autodeploy.lock.mode=in-memory} 일 때만 등록된다.
 *
 * <p>용도는 <b>테스트와 단일 노드 로컬 실행</b>이다. 프로세스 메모리에만 존재하므로 여러 인스턴스로
 * 스케일아웃하면 상호배제가 깨진다 — 운영에서는 {@link RedisDeploymentLockManager} 를 쓴다.
 *
 * <p>TTL 이 없는 것도 의도된 단순화다. 같은 JVM 안에서는 워커가 죽으면 프로세스가 같이 죽어
 * 맵도 사라지기 때문에, Redis 처럼 "죽은 소유자의 락이 영구히 남는" 문제가 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.lock.mode", havingValue = "in-memory")
public class InMemoryDeploymentLockManager implements DeploymentLockManager {

    private final ConcurrentMap<Long, Long> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(Long projectId, Long deploymentId) {
        // putIfAbsent 가 곧 SET NX 다 (원자적).
        return locks.putIfAbsent(projectId, deploymentId) == null;
    }

    @Override
    public boolean unlock(Long projectId, Long deploymentId) {
        // 2-인자 remove 가 compare-and-delete 다 — Redis 구현의 Lua 스크립트와 같은 의미.
        boolean released = locks.remove(projectId, deploymentId);
        if (!released) {
            log.warn("deploy lock was not released (already gone or owned by another). projectId={}",
                    projectId);
        }
        return released;
    }
}
