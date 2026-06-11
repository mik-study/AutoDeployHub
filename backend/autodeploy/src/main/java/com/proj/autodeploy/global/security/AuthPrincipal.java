package com.proj.autodeploy.global.security;

/**
 * 인증된 사용자 정보. SecurityContext 의 principal 로 저장된다.
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal principal} 로 주입받는다.
 */
public record AuthPrincipal(Long userId, String email) {
}
