# 4주차 현수 과제 5번 — Redis 분산락 가이드

> 작성일: 2026-06-16
> 대상: 4주차 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **5번 (Redis 분산락)** 만
> 참조: `05_api_spec.md §4.1`(409 `DEPLOYMENT_ALREADY_IN_PROGRESS`), 과제 1 중복 가드 seam
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.

---

## 1. 과제 범위

**프로젝트별 배포 동시성 제어.** 한 프로젝트에 진행 중 배포가 있으면 새 배포 요청을 막는다.
과제 1에서 임시로 둔 **DB 기반 in-progress 체크를 Redis 분산락으로 교체**한다.

- 동시 배포 시 → `DEPLOYMENT_ALREADY_IN_PROGRESS` (409)
- 락 키: 프로젝트 단위 (`deploy:lock:project:{projectId}`)

---

## 2. 사전 의존성

- 🔧 **`spring-boot-starter-data-redis` 의존성 추가 필요** (현재 `build.gradle`에 없음).
- ✅ Redis 인프라는 민준 3주차 `08_docker_compose`로 기동(연결 검증은 민준 과제 5).
- ✅ 과제 1에서 둔 `existsByProjectIdAndStatusIn` 기반 가드 → **이 자리를 락으로 대체** ("Redis 교체 예정" 주석을 실제로 반영).

`application.properties` 추가(예시): `spring.data.redis.host/port`.

---

## 3. 새로 만들 파일

```
deployment/
 └ lock/
     ├ DeploymentLockManager.java     # tryLock(projectId) / unlock(projectId)
     └ (RedisConfig 는 Boot 자동설정으로 대개 불필요; 필요 시 StringRedisTemplate 빈)
```

---

## 4. 구현 지침

### 4.1 락 획득 (POST 배포 시)
- 키: `deploy:lock:project:{projectId}`, 값: `deploymentId` 또는 토큰.
- **`SET key value NX EX <ttl>`** (원자적). `RedisTemplate.opsForValue().setIfAbsent(key, value, ttl)`.
- 실패(이미 존재) → `throw new ApiException(ErrorCode.DEPLOYMENT_ALREADY_IN_PROGRESS)` (409).
- 위치: 과제 1 `DeploymentService`의 중복 가드 지점. **deployment 생성·발행보다 먼저** 락 획득.

### 4.2 TTL (데드락 방지) ★
- Worker가 죽어 unlock을 못 해도 락이 영구히 남지 않도록 **TTL 필수**.
- TTL은 **최대 배포 소요시간보다 넉넉히**(예: 30분). 길게 걸리는 배포 대비, 필요 시 진행 중 TTL 갱신(heartbeat) — 1차는 단순 고정 TTL로 시작.

### 4.3 락 해제
- **deployment가 종착 상태(`isTerminal`) 도달 시 해제.** → 과제 2 컨슈머/과제 3 Worker의 종착 처리 훅에서 `unlock(projectId)` 호출.
- **안전 해제**: 값이 자신의 토큰일 때만 삭제(Lua compare-and-delete) — TTL 만료 후 다른 배포가 잡은 락을 실수로 지우지 않도록. 1차에 과하면 최소한 "내 deploymentId일 때만 삭제" 가드.
- 락 획득 후 발행 실패/예외 시 **즉시 해제**(try/catch/finally).

### 4.4 DB 가드 처리
- Redis를 1차 방어선으로. 과제 1의 `existsByProjectIdAndStatusIn`는 **보조 방어선으로 남기거나 제거** — 권장: 락을 주 경로로 하고 DB 체크 주석/삭제 정리(이중 관리 혼란 방지).

---

## 5. 연결축

- 과제 1: 중복 가드 자리를 교체.
- 과제 2/3: 종착 시 `unlock` 호출 훅(과제 2에서 남겨둔 "락 해제 훅" 자리).
- 5주차 rollback: 롤백도 동일 락 정책 적용(같은 프로젝트 동시 진행 금지) — 인터페이스 재사용.

---

## 6. 테스트

- **단위**: `DeploymentLockManager` — 첫 `tryLock` true, 연속 `tryLock` false, `unlock` 후 다시 true.
- **통합**: 같은 프로젝트 연속 POST 2회 → 2번째 409. 1번째가 종착 후 다시 POST → 성공.
- Testcontainers Redis 또는 임베디드 대체(없으면 락 매니저를 인터페이스화해 fake로 단위 검증).

## 7. 완료 체크리스트

- [ ] `spring-boot-starter-data-redis` 추가 + compose Redis 연결 성공
- [ ] `SET NX EX`로 원자적 획득, 실패 시 409
- [ ] TTL로 데드락 방지, 종착 시 안전 해제(소유 토큰 확인)
- [ ] 과제 1 DB 가드 → Redis 락으로 정리
- [ ] `./gradlew clean build` BUILD SUCCESSFUL

## 8. 하지 말 것

- Redisson 등 무거운 락 프레임워크 도입(MVP는 `SET NX`로 충분) — 단, 갱신/재진입 요건이 커지면 재검토
- 글로벌 락(전체 배포 직렬화) — **프로젝트 단위**만
- 락을 비즈니스 영속 상태로 사용 (락은 동시성 제어용, 상태는 `DeploymentStatus`가 진실)
