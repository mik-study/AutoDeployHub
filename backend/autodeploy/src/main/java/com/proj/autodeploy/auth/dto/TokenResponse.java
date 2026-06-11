package com.proj.autodeploy.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn
) {
}
