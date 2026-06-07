# 06. Sequence Diagrams

### (a) 수동 배포 정상 (Blue -> Green 전환)

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant FE as Vue Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MQ as RabbitMQ<br/>deploy.queue
    participant Worker as Worker
    participant Git as Git Repository
    participant Docker as Docker Host
    participant Green as Green Container
    participant Traefik as Traefik<br/>(Reverse Proxy)
    participant Blue as Blue Container
    participant Noti as Notification<br/>(Slack/Email)

    User->>FE: 배포 실행 클릭
    FE->>API: POST /api/deployments<br/>(수동 배포 요청)
    API->>DB: 배포 Job 생성<br/>(status=PENDING)
    DB-->>API: 배포 Job ID 반환
    API->>MQ: 배포 메시지 발행
    MQ-->>API: ACK
    API-->>FE: 배포 접수 응답<br/>(202 Accepted)

    MQ->>Worker: 배포 Job 전달
    Worker->>Git: 소스 코드 가져오기<br/>(Git Clone / Pull)
    Git-->>Worker: 소스 코드 전달
    Worker->>Docker: Docker 이미지 빌드
    Docker-->>Worker: Docker Build 성공
    Worker->>Docker: Green 컨테이너 실행
    Docker->>Green: 컨테이너 실행
    Green-->>Worker: 컨테이너 실행 완료
    Worker->>Green: Health Check 수행
    Green-->>Worker: Health Check 성공
    Worker->>Traefik: Traefik Route Switch<br/>(트래픽 전환)
    Traefik-->>Worker: Dynamic Config Update<br/>(Blue -> Green 적용 완료)
    Worker->>Docker: 기존 Blue 컨테이너 종료
    Docker->>Blue: Blue 컨테이너 종료
    Blue-->>Worker: Blue 컨테이너 종료 완료
    Worker->>DB: 배포 성공 기록<br/>(status=SUCCESS)
    DB-->>Worker: 업데이트 완료
    Worker->>Noti: 배포 성공 알림 발송
    Noti-->>Worker: 알림 전송 완료
    FE->>API: GET /api/deployments/{deploymentId}
    API->>DB: 배포 상태 조회
    DB-->>API: status=SUCCESS
    API-->>FE: 배포 성공 상태 응답<br/>(200 OK)
```

---

### (b) 빌드 실패 -> FAILED -> 알림

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant FE as Vue Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MQ as RabbitMQ<br/>deploy.queue
    participant Worker as Worker
    participant Git as Git Repository
    participant Docker as Docker Host
    participant Noti as Notification<br/>(Slack/Email)

    User->>FE: 배포 실행 클릭
    FE->>API: POST /api/deployments<br/>(수동 배포 요청)
    API->>DB: 배포 Job 생성<br/>(status=PENDING)
    DB-->>API: 배포 Job ID 반환
    API->>MQ: 배포 메시지 등록<br/>(Job ID 포함)
    MQ-->>API: 큐 등록 ACK
    API-->>FE: 배포 접수 응답<br/>(202 Accepted)
    MQ->>Worker: 배포 Job 전달

    Worker->>Git: Git Clone / Pull
    Git-->>Worker: 소스 코드 전달
    Worker->>Docker: Docker Build 실행
    Docker-->>Worker: Docker Build 실패
    Worker->>DB: status=FAILED 업데이트
    Worker->>DB: Build Error Log 저장<br/>(로그, 에러 정보)
    Worker->>Noti: 배포 실패 알림 전송<br/>(프로젝트, 환경, 에러 정보 포함)
    Noti-->>Worker: 알림 전송 완료
```

---

### (c) Health Check 실패 -> 기존 Blue 유지

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant FE as Vue Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MQ as RabbitMQ<br/>deploy.queue
    participant Worker as Worker
    participant Docker as Docker Host
    participant Green as Green Container<br/>(신규 버전)
    participant Blue as Blue Container<br/>(기존 버전)
    participant Noti as Notification<br/>(Slack/Email)

    User->>FE: 배포 실행 클릭
    FE->>API: POST /api/deployments<br/>(수동 배포 요청)
    API->>DB: 배포 Job 생성<br/>(status=PENDING)
    DB-->>API: 배포 Job ID 반환
    API->>MQ: 배포 메시지 등록<br/>(Job ID 포함)
    MQ-->>API: 큐 등록 ACK
    API-->>FE: 배포 접수 응답<br/>(202 Accepted)
    MQ->>Worker: 배포 Job 전달

    Worker->>Docker: Docker Pull & Start<br/>(새 버전 배포)
    Docker->>Green: Green 컨테이너 실행
    Green-->>Worker: 실행 완료
    Worker->>Green: Health Check 수행
    Green-->>Worker: Health Check 실패<br/>(응답 없음 / 비정상)
    Worker->>Docker: 실패한 Green 컨테이너 정리 요청
    Docker->>Green: 실패한 Green 컨테이너 제거
    Docker-->>Worker: 정리 완료<br/>(기존 Blue 유지)
    Worker->>DB: status=FAILED 업데이트
    Worker->>DB: Health Check 실패 로그 저장<br/>(에러 메시지 포함)
    Worker->>Noti: 배포 실패 알림 전송<br/>(프로젝트, 환경, 에러 정보 포함)
```

---

### (d) 코드 Push 후 자동 배포 (GitHub -> Webhook)

```mermaid
sequenceDiagram
    autonumber
    actor Dev as 개발자
    participant GitHub as Git Repository<br/>(GitHub)
    participant Webhook as Webhook<br/>(Receiver)
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MQ as RabbitMQ<br/>deploy.queue
    participant Worker as Worker
    participant Docker as Docker Host
    participant Traefik as Traefik<br/>(Reverse Proxy)
    participant Noti as Notification<br/>(Slack/Email)

    Dev->>GitHub: 코드 Push
    GitHub->>Webhook: Push 이벤트 발생<br/>(Webhook 전송)
    Webhook->>API: POST /api/webhooks/github<br/>(서명 포함)
    API->>API: Webhook Signature 검증
    API->>DB: 배포 Job 생성<br/>(status=PENDING)
    DB-->>API: 저장 결과 반환
    API->>MQ: 배포 메시지 등록<br/>(Job ID 포함)
    MQ-->>API: 큐 등록 ACK
    API-->>Webhook: Webhook 처리 접수 응답<br/>(202 Accepted)
    Webhook-->>GitHub: 202 Accepted
    MQ->>Worker: 배포 Job 전달

    Worker->>Docker: Docker Pull & Start<br/>(새 버전 배포)
    Docker-->>Worker: 실행 완료
    Worker->>Traefik: Health Check 수행
    Traefik-->>Worker: Health Check 성공<br/>(응답 OK / 정상)
    Worker->>Traefik: 트래픽 전환<br/>(Green 활성화)
    Worker->>Docker: 이전 Blue Container 중지 및 정리
    Docker-->>Worker: 정리 완료
    Worker->>DB: 배포 상태 업데이트<br/>(status=SUCCESS)
    Worker->>DB: 배포 성공 로그 저장<br/>(deploy log, stdout)
    Worker->>Noti: 배포 성공 알림 전송<br/>(프로젝트, 환경, 배포 정보 포함)
    Noti-->>Worker: 알림 전송 완료
```

---
