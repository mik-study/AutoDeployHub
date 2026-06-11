package com.proj.autodeploy.global.error;

import org.springframework.http.HttpStatus;

/**
 * MVP 에러 코드. (05_api_spec.md §0.4)
 */
public enum ErrorCode {

    // auth
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // user
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // project
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 프로젝트에 접근할 권한이 없습니다."),
    PROJECT_SUBDOMAIN_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 서브도메인입니다."),

    // environment variable
    ENV_KEY_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 환경변수 key 입니다."),
    ENV_NOT_FOUND(HttpStatus.NOT_FOUND, "환경변수를 찾을 수 없습니다."),

    // deployment
    DEPLOYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배포를 찾을 수 없습니다."),
    DEPLOYMENT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 배포가 있습니다."),
    DEPLOYMENT_NOT_CANCELABLE(HttpStatus.UNPROCESSABLE_ENTITY, "취소할 수 없는 배포 상태입니다."),
    DEPLOYMENT_NOT_ROLLBACKABLE(HttpStatus.UNPROCESSABLE_ENTITY, "롤백할 수 없는 배포입니다."),

    // webhook
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Webhook signature 검증에 실패했습니다."),

    // common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
