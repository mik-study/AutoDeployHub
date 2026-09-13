package com.proj.autodeploy.environment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 환경변수 값 수정. (05_api_spec.md §3.3)
 *
 * <p>key 는 바꿀 수 없다 — 유니크 제약 {@code uk_env_project_key} 와 컨테이너 주입 계약이
 * key 에 묶여 있어서, 이름을 바꾸려면 삭제 후 재등록하는 편이 의도가 분명하다.
 */
public record UpdateEnvRequest(
        @NotNull String value
) {
}
