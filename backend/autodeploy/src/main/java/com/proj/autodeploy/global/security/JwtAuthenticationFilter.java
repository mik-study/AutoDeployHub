package com.proj.autodeploy.global.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 파싱해 SecurityContext 에 인증을 채운다.
 * 토큰이 없거나 유효하지 않으면 인증을 채우지 않고 통과시킨다(이후 401 은 EntryPoint 가 처리).
 *
 * <p><b>SSE 예외</b> (과제 4): 브라우저 {@code EventSource} 는 커스텀 헤더를 붙일 수 없어서
 * 배포 로그 스트림만 {@code ?access_token=} 쿼리 파라미터를 받는다. 쿼리스트링은 액세스 로그와
 * 브라우저 히스토리에 남으므로 <b>해당 경로에서만</b> 허용한다 - 전역으로 열면 모든 API 요청의
 * 토큰이 로그에 남는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /** 쿼리 파라미터 인증을 허용하는 유일한 경로. (SSE) */
    private static final String STREAM_PATH_SUFFIX = "/logs/stream";
    private static final String TOKEN_PARAM = "access_token";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parse(token);
                if (tokenProvider.isAccessToken(claims)) {
                    AuthPrincipal principal = new AuthPrincipal(
                            tokenProvider.getUserId(claims), tokenProvider.getEmail(claims));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 유효하지 않은 토큰 → 인증 미설정, 보호된 자원 접근 시 401
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        if (isSseStreamRequest(request)) {
            String param = request.getParameter(TOKEN_PARAM);
            if (param != null && !param.isBlank()) {
                return param;
            }
        }
        return null;
    }

    private boolean isSseStreamRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.endsWith(STREAM_PATH_SUFFIX);
    }
}
