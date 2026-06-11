package com.proj.autodeploy.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * jwt.* 설정 바인딩.
 * - jwt.secret
 * - jwt.access-token-validity-seconds
 * - jwt.refresh-token-validity-seconds
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds
) {
}
