# Docker Compose Local Infra Test

## 1. 목적

루트의 `docker-compose.yml`로 AutoDeploy 로컬 인프라가 정상 기동되는지 검증한다.

검증 범위는 다음과 같다.

- PostgreSQL 기동 및 healthcheck
- Redis 기동 및 healthcheck
- RabbitMQ 기동 및 management UI 접근
- Traefik dashboard 접근
- Traefik Docker provider 기반 자동 라우팅 PoC

## 2. 실행 대상

```bash
docker compose up -d
```

구성 서비스:

| Service    | Container             | Image                   | Port                                |
| ---------- | --------------------- | ----------------------- | ----------------------------------- |
| PostgreSQL | `autodeploy-postgres` | `postgres:16`           | `127.0.0.1:5432`                    |
| Redis      | `autodeploy-redis`    | `redis:7`               | `127.0.0.1:6379`                    |
| RabbitMQ   | `autodeploy-rabbitmq` | `rabbitmq:3-management` | `127.0.0.1:5672`, `127.0.0.1:15672` |
| Traefik    | `autodeploy-traefik`  | `traefik:v3.6.1`        | `127.0.0.1:80`, `127.0.0.1:8080`    |

공통 네트워크:

```text
autodeploy-network
```

---

## 3. 기본 기동 검증

### 3-1. Compose 설정 확인

```bash
docker compose config
```

기대 결과:

- YAML 파싱 성공
- `postgres`, `redis`, `rabbitmq`, `traefik` 서비스가 모두 출력됨
- `autodeploy-network` 네트워크가 출력됨

### 3-2. 컨테이너 상태 확인

```bash
docker compose ps
```

기대 결과:

```text
autodeploy-postgres   Up ... (healthy)
autodeploy-redis      Up ... (healthy)
autodeploy-rabbitmq   Up ... (healthy)
autodeploy-traefik    Up ... (healthy)
```

### 3-3. 서비스별 healthcheck 직접 확인

PostgreSQL:

```bash
docker exec autodeploy-postgres pg_isready -U postgres -d autodeploy
```

Redis:

```bash
docker exec autodeploy-redis redis-cli ping
```

RabbitMQ:

```bash
docker exec autodeploy-rabbitmq rabbitmq-diagnostics -q ping
```

Traefik:

```bash
docker exec autodeploy-traefik traefik healthcheck --ping
```

---

## 4. Dashboard 접근 확인

### 4-1. RabbitMQ Management UI

URL:

```text
http://localhost:15672
```

기본 계정:

```text
username: autodeploy_user
password: autodeploy_password
```

### 4-2. Traefik Dashboard

URL:

```text
http://localhost:8082
```

기본적으로 다음 internal router가 표시된다.

- `api@internal`
- `dashboard@internal`
- `ping@internal`

---

## 5. Traefik 자동 라우팅 PoC

### 5-1. 목적

Traefik이 Docker provider를 통해 컨테이너 label을 읽고, Host 기반 라우터를 자동 생성하는지 검증한다.

검증 흐름:

```text
whoami.autodeploy.dev
-> 127.0.0.1:80
-> Traefik web entrypoint
-> whoami@docker router
-> whoami service
-> whoami-demo container
```

### 5-2. hosts 설정

로컬 브라우저에서 도메인으로 접근하기 위해 `/etc/hosts`에 다음 항목을 추가했다.

```text
127.0.0.1 whoami.autodeploy.dev
```

hosts를 수정하지 않아도 다음 방식으로 Host 헤더를 직접 넣어 테스트할 수 있다.

```bash
curl -H "Host: whoami.autodeploy.dev" http://127.0.0.1
```

### 5-3. 테스트 컨테이너 실행

```bash
docker run -d \
  --name whoami-demo \
  --network autodeploy-network \
  --label "traefik.enable=true" \
  --label "traefik.http.routers.whoami.rule=Host(\`whoami.autodeploy.dev\`)" \
  --label "traefik.http.routers.whoami.entrypoints=web" \
  --label "traefik.http.services.whoami.loadbalancer.server.port=80" \
  traefik/whoami
```

label 의미:

| Label                                                      | 의미                                                  |
| ---------------------------------------------------------- | ----------------------------------------------------- |
| `traefik.enable=true`                                      | 해당 컨테이너를 Traefik 라우팅 대상으로 등록          |
| `traefik.http.routers.whoami.rule=Host(...)`               | `whoami.autodeploy.dev` 요청을 `whoami` 라우터가 처리 |
| `traefik.http.routers.whoami.entrypoints=web`              | 80번 HTTP entrypoint로 들어온 요청 처리               |
| `traefik.http.services.whoami.loadbalancer.server.port=80` | 컨테이너 내부 80번 포트로 프록시                      |

### 5-4. 접속 확인

```bash
curl http://whoami.autodeploy.dev
```

또는 브라우저:

```text
http://whoami.autodeploy.dev
```

성공 시 `traefik/whoami` 컨테이너 정보가 응답된다.

### 5-5. Traefik Dashboard 확인

Traefik dashboard의 HTTP Routers 목록에 다음 라우터가 표시되면 성공이다.

```text
whoami@docker
```

확인된 라우터 정보:

| 항목        | 값                                | 의미                            |
| ----------- | --------------------------------- | ------------------------------- |
| Rule        | `Host(\`whoami.autodeploy.dev\`)` | 해당 Host 요청을 매칭           |
| Entrypoints | `web`                             | 80번 HTTP entrypoint 사용       |
| Name        | `whoami@docker`                   | Docker provider가 생성한 라우터 |
| Service     | `whoami`                          | 요청을 전달할 Traefik service   |
| Provider    | `docker`                          | Docker label에서 생성됨         |

즉 다음 동작을 확인했다.

```text
Traefik Docker provider 정상
Docker socket 접근 정상
whoami 컨테이너 label 인식 정상
Host 기반 자동 라우팅 정상
```

---

## 6. 정리 명령

whoami PoC 컨테이너만 제거:

```bash
docker rm -f whoami-demo
```

로컬 인프라 전체 종료:

```bash
docker compose down
```

볼륨까지 초기화:

```bash
docker compose down -v
```

`down -v`는 PostgreSQL, Redis, RabbitMQ 데이터 볼륨까지 삭제하므로 테스트 데이터를 보존해야 하면 사용하지 않는다.
