package com.proj.autodeploy.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 환경변수 등록. (05_api_spec.md §3.1)
 *
 * <p>key 는 셸 환경변수 관례(대문자/숫자/언더스코어, 숫자로 시작 금지)를 강제한다.
 * {@code docker run -e KEY=VALUE} 로 그대로 주입되기 때문에, 여기서 막지 않으면
 * 컨테이너 실행 시점에야 터진다.
 */
public record CreateEnvRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]*$",
                message = "key 는 영문자/언더스코어로 시작하고 영문자·숫자·언더스코어만 사용할 수 있습니다.")
        String key,

        @NotNull
        String value,

        Boolean isSecret
) {

    /** 미지정 시 secret 으로 취급한다 — 실수로 평문 노출되는 쪽보다 마스킹되는 쪽이 안전하다. */
    public boolean secretOrDefault() {
        return isSecret == null || isSecret;
    }
}
