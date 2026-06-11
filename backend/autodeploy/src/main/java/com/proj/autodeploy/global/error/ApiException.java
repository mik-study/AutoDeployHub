package com.proj.autodeploy.global.error;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public static ApiException of(ErrorCode code, String key, Object value) {
        Map<String, Object> details = new HashMap<>();
        details.put(key, value);
        return new ApiException(code, code.defaultMessage(), details);
    }
}
