package com.proj.autodeploy.environment.dto;

import java.time.Instant;

/**
 * 환경변수 목록 행. (05_api_spec.md §3.2)
 *
 * <p>등록/수정 응답({@link EnvResponse})과 필드가 달라서 별도 record 로 둔다
 * (목록은 {@code value}/{@code updatedAt}, 등록은 {@code projectId}/{@code createdAt}).
 *
 * <p>{@code isSecret=true} 면 {@code value} 는 {@link #MASKED} 로 채워진다. 마스킹은 서비스가
 * 담당하고 이 record 는 결과만 담는다 — 복호화한 값이 여기까지 흘러오지 않게 하는 게 요점이다.
 */
public record EnvListItemResponse(
        Long envId,
        String key,
        String value,
        boolean isSecret,
        Instant updatedAt
) {

    /** 민준 환경변수 화면과 합의한 마스킹 표기. */
    public static final String MASKED = "****";
}
