package com.proj.autodeploy.deployment.lock;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis {@code SET NX EX} 기반 분산락. (과제 5 가이드 §4)
 *
 * <p>Redisson 같은 락 프레임워크를 쓰지 않은 건 MVP 요건이 "프로젝트 단위 상호배제" 하나뿐이기
 * 때문이다. 재진입·공정성·자동 갱신이 필요해지면 그때 재검토한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.lock.mode", havingValue = "redis", matchIfMissing = true)
public class RedisDeploymentLockManager implements DeploymentLockManager {

    private static final String KEY_PREFIX = "deploy:lock:project:";

    /**
     * compare-and-delete. GET 후 DEL 을 따로 하면 그 사이에 TTL 이 만료되고 다른 배포가 락을 잡을 수
     * 있어서, 한 번의 원자적 실행으로 묶어야 한다.
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisDeploymentLockManager(
            StringRedisTemplate redisTemplate,
            @Value("${autodeploy.lock.ttl-seconds:1800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean tryLock(Long projectId, Long deploymentId) {
        // setIfAbsent(key, value, ttl) = SET key value NX EX ttl. 획득과 TTL 설정이 한 번에 일어나야
        // 한다 - SET 후 EXPIRE 로 나누면 그 사이에 죽었을 때 TTL 없는 락이 영구히 남는다.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(projectId), token(deploymentId), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean unlock(Long projectId, Long deploymentId) {
        List<String> keys = Collections.singletonList(key(projectId));
        Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, keys, token(deploymentId));
        boolean released = deleted != null && deleted > 0;
        if (!released) {
            // TTL 만료 후 다른 배포가 락을 잡았거나, 이미 해제된 경우. 정상 동작일 수 있어 warn 까지만.
            log.warn("deploy lock was not released (already gone or owned by another). projectId={}",
                    projectId);
        }
        return released;
    }

    private String key(Long projectId) {
        return KEY_PREFIX + projectId;
    }

    /** 락의 값 = 소유 배포 id. unlock 의 compare 대상이다. */
    private String token(Long deploymentId) {
        return String.valueOf(deploymentId);
    }
}
