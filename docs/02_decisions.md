# 02. 설계 의사결정 (Decisions)

> 1주차 도메인 분석(가이드 .md) 및 Notion 아키텍처 다이어그램 사이의 충돌 지점을
> 2주차 진입 전 정리하기 위한 의사결정 문서.
>
> - 회의일자: 2026-05-27
> - 참석자: 스터디원 2인
> - 기준 문서: `01_domain_analysis.md`, Notion 아키텍처 페이지

---

## 1. 의사결정 요약표

| # | 항목 | 가이드(.md) | Notion | **최종 결정 (2026-05-27)** |
|---|---|---|---|---|
| 1 | Reverse Proxy | Traefik | Nginx | **Traefik** |
| 2 | Image Registry | Amazon ECR | 미정 (로컬 Docker?) | **로컬 Docker (추후 ECR)** |
| 3 | Runtime 환경 | AWS EC2 | Docker Host (self-hosted) | **로컬 (추후 AWS EC2)** |
| 4 | 실패 대응 전략 | 이전 이미지로 Rollback | Blue-Green 무중단 | **Blue-Green 무중단** |
| 5 | 모니터링 스택 | CloudWatch + Prometheus + Loki | Prometheus + ELK | **Prometheus + ELK** |
| 6 | Source Repository | GitHub만 | GitHub / GitLab | **GitHub** |
| 7 | Queue 구조 | deploy queue 1개 | deploy / cleanup / notification / log-processing 4개 + DLQ | **deploy queue 1개 (추후 확장)** |
| 8 | 모바일 앱 | Flutter 포함 | 없음 (Web only) | **추후 (MVP 제외)** |

---

## 2. 항목별 결정 사유

### 2.1 Reverse Proxy → Traefik

- **선택**: Traefik
- **사유**:
  - Docker label 기반의 동적 라우팅이 강력 → 컨테이너가 뜨고 내려갈 때 설정 reload 불필요
  - 사용자 앱마다 서브도메인 자동 매핑이 자연스러움 (`project-12.autodeploy.dev`)
  - 1주차 가이드의 라우팅 설계가 Traefik 기준이라 재작업 부담 최소
- **영향**:
  - Worker가 컨테이너 실행 시 Traefik label을 함께 부여하는 로직 필요
  - Notion 다이어그램 중 Nginx로 그려진 부분은 다음 다이어그램 갱신 시 Traefik으로 교체

### 2.2 Image Registry → 로컬 Docker (추후 ECR)

- **선택**: MVP는 로컬 Docker image 저장소 사용, 차후 단계에서 ECR 도입
- **사유**:
  - AWS 비용/계정 세팅 부담을 MVP 단계에서 회피
  - 빌드 Worker와 Runtime이 같은 머신에 있으면 push/pull 자체가 불필요
  - 이미지 태그 전략(`autodeploy-runtime:project-{id}-deploy-{seq}`)은 동일하게 유지하여 ECR 전환 시 코드 변경 최소화
- **영향**:
  - 1차 구현: `docker build` 후 같은 호스트에서 바로 `docker run`
  - 2차 확장: `docker push <ECR_URI>` 단계 추가 + Runtime이 다른 머신일 때 pull 단계 추가

### 2.3 Runtime 환경 → 로컬 (추후 AWS EC2)

- **선택**: MVP는 로컬 Docker Host, 차후 AWS EC2로 확장
- **사유**:
  - Registry 결정과 동일 — 비용/계정 부담 회피
  - 로컬에서 전체 흐름(빌드 → 배포 → 헬스체크 → Blue-Green 전환)을 먼저 검증
- **영향**:
  - Runtime 접근 추상화(`RuntimeDeployService` 인터페이스)를 두고
    - 1차: `LocalDockerRuntimeAdapter`
    - 2차: `Ec2DockerRuntimeAdapter` (SSH / Docker remote API 등)
  - 인프라 의존이 코드에 직접 박히지 않도록 설계

### 2.4 실패 대응 → Blue-Green 무중단

- **선택**: Blue-Green 배포
- **사유**:
  - Notion에 이미 Blue-Green 다이어그램이 완성되어 있어 설계 자산 활용 가능
  - 무중단 배포라는 학습 가치 큼
  - 사실상 "이전 버전으로 즉시 복귀"가 가능 → 가이드의 Rollback 요구사항도 커버
- **영향**:
  - `RuntimeInstance` 엔티티에 다음 필드 추가 필요
    - `color`: `BLUE` / `GREEN`
    - `port`: 예) 8081 / 8082
    - `isActive`: 현재 트래픽 받는 쪽인지 여부
  - Deployment 상태머신에 Blue-Green 관련 전이 추가
    - `DEPLOYING` → `HEALTH_CHECKING` → `SWITCHING_TRAFFIC` → `SUCCEEDED`
    - 실패 시 트래픽은 기존 Active 쪽 유지 → 새로 띄운 쪽만 정리
  - Traefik 라우팅 변경(weighted service / 우선순위 라우터 등)으로 트래픽 전환

### 2.5 모니터링 → Prometheus + ELK

- **선택**: Prometheus + Grafana (메트릭) + ELK Stack (로그)
- **사유**:
  - CloudWatch는 AWS 종속이라 로컬 우선 정책과 어긋남
  - ELK는 로그 분석 학습 가치도 큼
- **MVP 범위**:
  - **1차 필수**: 애플리케이션이 Prometheus 메트릭 endpoint(`/actuator/prometheus`)만 노출
  - **1차 선택**: Grafana 대시보드 1~2개
  - **2차 확장**: ELK 풀스택 (Elasticsearch + Logstash + Filebeat + Kibana)
- **주의**:
  - ELK는 메모리 사용량이 크므로 로컬 환경에서는 부담될 수 있음
  - 우선 application log를 파일로 떨구는 것까지만 1차 목표

### 2.6 Source Repository → GitHub만

- **선택**: GitHub만 지원
- **사유**:
  - Webhook signature 검증 / OAuth / API 스펙이 플랫폼마다 달라 둘 다 지원하면 추상화 비용 증가
  - 스터디 인원 2명 기준 GitHub만으로 충분
- **영향**:
  - GitLab 관련 클래스/엔드포인트는 MVP에서 만들지 않음
  - 단, `GitProvider` 인터페이스로 추상화는 해두고 구현체는 `GitHubProvider` 하나만

### 2.7 Queue 구조 → deploy queue 1개 (추후 확장)

- **선택**: `deploy.queue` 1개로 시작
- **사유**:
  - 4개 queue + DLQ는 학습 의미는 있으나 MVP 동작 검증에는 과함
  - 단일 큐로 전체 흐름이 동작하는 것을 먼저 확인
- **2차 확장 시 분리 후보**:
  - `cleanup.queue` — 오래된 컨테이너/이미지 정리
  - `notification.queue` — 알림 발송
  - `log-processing.queue` — 대용량 로그 비동기 적재
  - DLQ — 재시도 한도 초과 메시지 보관

### 2.8 모바일 앱 → 추후 (MVP 제외)

- **선택**: Flutter 모바일 앱은 MVP에서 제외
- **사유**:
  - 2인 인원으로 Web + Backend + Infra 만으로도 충분히 도전적
  - 모바일은 별도 학습 비용 발생
- **2차 확장 시**: Flutter로 "운영 모니터링 + 긴급 롤백" 정도의 가벼운 앱

---

## 3. MVP 범위 (확정)

위 결정을 반영한 1차(MVP) 범위는 다음과 같다.

```text
[포함]
- GitHub public repository 등록
- Dockerfile 기반 빌드
- 로컬 Docker로 이미지 저장
- 로컬 Docker Host에 컨테이너 실행
- Traefik 서브도메인 라우팅
- Blue-Green 방식 무중단 배포 (실패 시 기존 Active 유지)
- Health Check
- deploy.queue 단일 큐 기반 비동기 처리
- 실시간 배포 로그 (SSE)
- 배포 이력 조회
- Prometheus 메트릭 노출 (+ Grafana 대시보드 1~2개)

[제외 - 추후 단계]
- AWS EC2 / ECR
- ELK 풀스택 구성
- 다중 Queue + DLQ
- Flutter 모바일 앱
- GitLab 지원
- Kubernetes / ECS
- Canary, Multi-region, Auto-scaling
- Custom domain, DB migration 자동화, Preview deployment
```

---

## 4. 후속 영향 / TODO

다음 산출물 작성 시 이 결정을 반영해야 한다.

- [ ] `03_erd.md` — `RuntimeInstance`에 `color`, `port`, `isActive` 필드 반영
- [ ] `04_state_machine.md` — Blue-Green 전환 상태(`SWITCHING_TRAFFIC` 등) 추가
- [ ] `05_api_spec.md` — GitLab 관련 endpoint 제외, ECR/AWS 의존 응답 필드 제외
- [ ] `06_sequence_diagrams.md`
  - 수동 배포 정상 (Blue → Green 전환까지)
  - 빌드 실패 → FAILED → 알림
  - Health Check 실패 → Blue로 트래픽 유지 (즉시 롤백)
  - Webhook 자동 배포 (Signature 검증 포함)
- [ ] `07_wireframes/` — 프로젝트 상세 화면에 Blue/Green 현재 상태 표시 영역 추가
- [ ] `08_docker-compose.yml`
  - postgres, redis, rabbitmq, traefik
  - (ELK는 2차로 미루므로 1차 compose에는 포함하지 않음)

---

## 5. 변경 이력

| 일자 | 내용 | 비고 |
|---|---|---|
| 2026-05-27 | 최초 작성, 8개 항목 결정 | 스터디 2주차 진입 전 |
