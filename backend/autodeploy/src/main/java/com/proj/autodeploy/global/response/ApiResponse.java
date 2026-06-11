package com.proj.autodeploy.global.response;

/**
 * 단건 성공 응답. (05_api_spec.md §0.1) { "data": ... }
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
