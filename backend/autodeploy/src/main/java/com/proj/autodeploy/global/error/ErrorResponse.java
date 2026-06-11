package com.proj.autodeploy.global.error;

import java.util.Map;

/**
 * 에러 응답 포맷. (05_api_spec.md §0.1)
 * { "error": { "code": ..., "message": ..., "details": {...} } }
 *
 * <p>null 필드(details 등)는 spring.jackson.default-property-inclusion=non_null 설정으로 직렬화에서 제외된다.
 */
public record ErrorResponse(Body error) {

    public record Body(String code, String message, Map<String, Object> details) {
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, message, details));
    }
}
