# 4주차 현수 과제 6번 — EnvironmentVariable API 가이드

> 작성일: 2026-06-16
> 대상: 4주차 현수 담당 — 백엔드 deployment 도메인
> 범위: `00_progress_history.md` §4 현수 과제 **6번 (EnvironmentVariable API)** 만
> 참조: `05_api_spec.md §3`, `03_erd.md`(environment_variables)
> 비고: 본 문서는 **가이드**이며 코드 구현은 포함하지 않는다.
> 우선순위: 코어(과제 1~5) 다음. 분량 과다 시 **키 등록/조회까지 우선**, 민준 환경변수 화면과 짝.

---

## 1. 과제 범위

프로젝트별 환경변수 CRUD. **값은 암호화 저장, 조회 시 마스킹**, 컨테이너 run 시 주입.

| # | 엔드포인트 | 응답 | 명세 |
|---|---|---|---|
| ① | `POST /api/projects/{projectId}/env` | 201 | §3.1 |
| ② | `GET /api/projects/{projectId}/env` | 200 (secret 마스킹) | §3.2 |
| ③ | `PATCH /api/projects/{projectId}/env/{envId}` | 200 | §3.3 |
| ④ | `DELETE /api/projects/{projectId}/env/{envId}` | 204 | §3.4 |

---

## 2. 사전 자산 (이미 있음 — 거의 다 갖춰짐)

- ✅ `EnvironmentVariable` 엔티티 — `projectId`, `key`(`env_key`), `encryptedValue`, `secret`(`is_secret`), `changeValue()`
- ✅ 유니크 제약 `uk_env_project_key (project_id, env_key)` — 중복 키 DB 레벨 차단
- ✅ Repository — `findByProjectId`, `findByIdAndProjectId`, `existsByProjectIdAndKey` **전부 존재** (추가 메서드 불필요)
- ✅ `ErrorCode.ENV_KEY_DUPLICATED(409)`, `ENV_NOT_FOUND(404)`
- ✅ 공통 패턴(`ApiResponse.of` / `AuthPrincipal` / `ApiException`)

→ **엔티티·Repository·에러코드는 손댈 게 없다.** 컨트롤러/서비스/DTO + **암호화 컴포넌트**만 추가.

---

## 3. 새로 만들 파일

```
environment/
 ├ EnvironmentVariableController.java   # ①~④
 ├ EnvironmentVariableService.java      # CRUD + 암복호화 + 마스킹
 ├ crypto/
 │   └ EnvCrypto.java                   # AES-GCM 암복호화 (앱 키)
 └ dto/
     ├ CreateEnvRequest.java            # { key, value, isSecret }
     ├ UpdateEnvRequest.java            # { value }
     └ EnvResponse.java                 # 등록/목록 응답 (value 마스킹 처리)
```

---

## 4. 구현 지침

### 4.1 암호화 (`EnvCrypto`)
- **대칭키 AES/GCM** 권장(무결성 태그 포함). 키는 `application.properties`의 `env.encryption-key`로 받되 **운영은 환경변수 주입**(JWT_SECRET과 동일 원칙).
  ```
  env.encryption-key=${ENV_ENC_KEY:local-dev-32byte-key-change-me!!}
  ```
- `encrypt(plain) → base64(iv+cipher+tag)`, `decrypt(stored) → plain`. 저장은 이 결과를 `encryptedValue`에.
- ⚠️ 과제 1 메모: `Project.webhookSecret`도 "TODO 암호화 저장"이 달려 있음 → 같은 `EnvCrypto`를 공용 컴포넌트로 두면 재사용 가능(범위는 env 우선).

### 4.2 등록 (POST) — §3.1 → 201
- 소유권 검증(과제 1의 `ProjectService` public 검증 재사용).
- 중복: `existsByProjectIdAndKey` true → `ENV_KEY_DUPLICATED`(409). (DB 유니크 제약은 최후 방어선.)
- `value` 암호화 → `EnvironmentVariable.builder()` 저장.
- 응답: `{ envId, projectId, key, isSecret, createdAt }` — **value 미포함**(명세 그대로).

### 4.3 목록 (GET) — §3.2 → 200 (★ 마스킹)
- `findByProjectId(projectId)`.
- **`isSecret=true` → `value="****"`**, `isSecret=false` → **복호화한 실제 값** 반환.
- 응답 행: `{ envId, key, value, isSecret, updatedAt }`.

### 4.4 수정 (PATCH) — §3.3 → 200
- `findByIdAndProjectId(envId, projectId)` 없으면 `ENV_NOT_FOUND`(404).
- 새 `value` 암호화 → `entity.changeValue(encrypted)`. 응답은 3.1 형태.

### 4.5 삭제 (DELETE) — §3.4 → 204
- `findByIdAndProjectId` 확인 후 삭제. `ResponseEntity.noContent()`.

### 4.6 컨테이너 주입 (과제 3 연결)
- Worker가 `docker run` 시 해당 프로젝트 env 전체를 **복호화**해 `-e KEY=VALUE`로 주입.
- 이를 위해 서비스에 **내부용 복호화 조회 메서드**(마스킹 X, Worker 전용) 별도 제공 — 외부 GET과 분리.

---

## 5. 연결축

- 과제 3 Worker: run 시 env 주입(복호화).
- 민준 과제 4(환경변수 관리 화면): 목록 마스킹 표시·추가·수정·삭제 계약. **secret 마스킹 표시 규칙**(`****`) 합의.

---

## 6. 테스트

- **단위**: `EnvCrypto` 암복호화 라운드트립; 마스킹 분기(secret/non-secret).
- **통합**: POST(201) → 중복 POST(409) → GET(secret은 `****`, 일반은 값) → PATCH(값 변경 반영) → DELETE(204) → GET 404 경로.
- 저장값이 평문이 아님(DB에 `encryptedValue`가 원문과 다름) 확인.

## 7. 완료 체크리스트

- [ ] 4개 엔드포인트가 §3.1~3.4 응답 포맷 준수
- [ ] 값 **암호화 저장**(평문 미저장), GET에서 secret `****` 마스킹
- [ ] `ENV_KEY_DUPLICATED`(409) / `ENV_NOT_FOUND`(404) / 403(타인 프로젝트) 매핑
- [ ] Worker 주입용 복호화 조회 메서드(외부 GET과 분리)
- [ ] 암호화 키는 환경변수 주입 가능(`ENV_ENC_KEY`)
- [ ] `./gradlew clean build` BUILD SUCCESSFUL

## 8. 하지 말 것

- KMS/외부 비밀관리(MVP는 앱 대칭키) — 운영 전환 시 재검토
- secret 값을 평문 로그/응답에 노출
- 엔티티·Repository·ErrorCode 변경(이미 충분)
