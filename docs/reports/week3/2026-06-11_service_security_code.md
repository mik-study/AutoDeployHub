# 서비스 로직 & 인증/보안 코드 전문 (현수)

> AutoDeployHub `backend/autodeploy` 의 **도메인 서비스 로직**과 **global 패키지의 auth/JWT/security 관련 코드**를
> 전문(full source)으로 싣고 각 파일에 간단한 설명을 단 문서.
>
> - 작성일: 2026-06-11
> - 담당: 김현수
> - 대상: `auth/AuthService`, `project/ProjectService`, `global/security/*`, `global/config/SecurityConfig`

---

## 목차

1. 도메인 서비스 로직
   - 1.1 `AuthService`
   - 1.2 `ProjectService`
2. 인증/보안 (global)
   - 2.1 `AuthPrincipal`
   - 2.2 `JwtProperties`
   - 2.3 `JwtTokenProvider`
   - 2.4 `JwtAuthenticationFilter`
   - 2.5 `SecurityConfig`
3. 요청 처리 흐름 한눈에 보기

> 참고: `user/environment/deployment/runtime/webhook` 도메인은 3주차 기준 **엔티티·Repository만** 있고 Service는 아직 없다(4주차 예정). 따라서 현재 서비스 로직은 `AuthService`·`ProjectService` 둘이다.

---

# 1. 도메인 서비스 로직

## 1.1 `auth/AuthService.java`

**설명**: 회원가입·로그인·토큰 갱신의 핵심 비즈니스 로직.
- `signup`: 이메일 중복 검사 → 비밀번호 **BCrypt 해시** 후 저장.
- `login`: 이메일로 사용자 조회 → 비밀번호 일치 검사 → 실패 시 동일한 `AUTH_INVALID_CREDENTIALS`(계정 존재 여부를 노출하지 않음) → 토큰 발급.
- `refresh`: refresh 토큰 파싱(만료/위조 구분) → refresh 타입 확인 → 사용자 재조회 → 새 토큰 발급.
- 조회 메서드는 `@Transactional(readOnly = true)` 로 성능/안전성 확보.

```java
package com.proj.autodeploy.auth;

import com.proj.autodeploy.auth.dto.LoginRequest;
import com.proj.autodeploy.auth.dto.SignupRequest;
import com.proj.autodeploy.auth.dto.SignupResponse;
import com.proj.autodeploy.auth.dto.TokenResponse;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.user.UserRepository;
import com.proj.autodeploy.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.of(ErrorCode.USER_EMAIL_DUPLICATED, "email", request.email());
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();
        return SignupResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        User user = userRepository.findById(tokenProvider.getUserId(claims))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken, tokenProvider.accessTokenValiditySeconds());
    }
}
```

---

## 1.2 `project/ProjectService.java`

**설명**: 프로젝트 CRUD + 소유권 검증 + subdomain/webhookSecret 생성.
- `create`: subdomain 중복 검사 → webhookSecret 생성 → 저장. **subdomain 미지정 시** `IDENTITY` 즉시 INSERT 제약 때문에 임시 placeholder로 INSERT 후 `project-{id}` 로 갱신(주석 참고).
- `list`: 본인 소유 + `ACTIVE` 상태만 페이지 조회.
- `get/update/delete`: 공통 `getOwnedProject()` 로 **존재(404) + 소유권(403)** 검사. `update` 는 부분 수정, `delete` 는 `archive()`(soft delete).
- `generateWebhookSecret`: `SecureRandom` 24바이트 → `whs_` + hex.

```java
package com.proj.autodeploy.project;

import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.project.domain.ProjectStatus;
import com.proj.autodeploy.project.dto.CreateProjectRequest;
import com.proj.autodeploy.project.dto.CreateProjectResponse;
import com.proj.autodeploy.project.dto.ProjectResponse;
import com.proj.autodeploy.project.dto.ProjectSummaryResponse;
import com.proj.autodeploy.project.dto.UpdateProjectRequest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    // MVP: 단일 호스트의 webhook 수신 endpoint. (운영 도메인 확정 시 설정으로 분리)
    private static final String WEBHOOK_URL = "https://api.autodeploy.dev/api/webhooks/github";

    private final ProjectRepository projectRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public CreateProjectResponse create(Long userId, CreateProjectRequest request) {
        String requestedSubdomain = normalize(request.subdomain());
        if (requestedSubdomain != null && projectRepository.existsBySubdomain(requestedSubdomain)) {
            throw ApiException.of(ErrorCode.PROJECT_SUBDOMAIN_DUPLICATED, "subdomain", requestedSubdomain);
        }

        String plainSecret = generateWebhookSecret();

        // subdomain 은 NOT NULL + UNIQUE. IDENTITY 전략이라 save() 시 즉시 INSERT 되므로,
        // 미지정 시 임시 placeholder 로 INSERT 한 뒤 발급된 id 로 project-{id} 로 갱신한다.
        boolean autoGenerate = (requestedSubdomain == null);
        String initialSubdomain = autoGenerate
                ? "pending-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : requestedSubdomain;

        Project project = Project.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .repositoryUrl(request.repositoryUrl())
                .defaultBranch(request.defaultBranch())
                .rootDirectory(request.rootDirectory())
                .healthCheckPath(request.healthCheckPath())
                .healthCheckPort(request.healthCheckPort())
                .healthCheckTimeoutSeconds(request.healthCheckTimeoutSeconds())
                .healthCheckIntervalSeconds(request.healthCheckIntervalSeconds())
                .subdomain(initialSubdomain)
                .webhookSecret(plainSecret)    // TODO(4주차): 암호화 저장
                .build();

        project = projectRepository.save(project);

        if (autoGenerate) {
            project.assignSubdomain("project-" + project.getId());
        }

        return CreateProjectResponse.from(project, WEBHOOK_URL, plainSecret);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummaryResponse> list(Long userId, Pageable pageable) {
        return projectRepository.findByOwnerIdAndStatus(userId, ProjectStatus.ACTIVE, pageable)
                .map(ProjectSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        return ProjectResponse.from(getOwnedProject(userId, projectId));
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, UpdateProjectRequest request) {
        Project project = getOwnedProject(userId, projectId);
        project.patch(
                request.description(),
                request.defaultBranch(),
                request.rootDirectory(),
                request.healthCheckPath(),
                request.healthCheckPort(),
                request.healthCheckTimeoutSeconds(),
                request.healthCheckIntervalSeconds(),
                request.name()
        );
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.archive(); // soft delete
    }

    private Project getOwnedProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.PROJECT_NOT_FOUND, "projectId", projectId));
        if (!project.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return project;
    }

    private String normalize(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            return null;
        }
        return subdomain.trim();
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "whs_" + HexFormat.of().formatHex(bytes);
    }
}
```

---

# 2. 인증/보안 (global)

## 2.1 `global/security/AuthPrincipal.java`

**설명**: 인증된 사용자 정보를 담는 record. `SecurityContext` 의 principal 로 저장되고, 컨트롤러에서 `@AuthenticationPrincipal AuthPrincipal` 로 주입받는다. (userId 로 소유권 검증에 사용)

```java
package com.proj.autodeploy.global.security;

/**
 * 인증된 사용자 정보. SecurityContext 의 principal 로 저장된다.
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal principal} 로 주입받는다.
 */
public record AuthPrincipal(Long userId, String email) {
}
```

---

## 2.2 `global/security/JwtProperties.java`

**설명**: `application.properties` 의 `jwt.*` 값을 바인딩하는 설정 record. `SecurityConfig` 의 `@EnableConfigurationProperties(JwtProperties.class)` 로 활성화된다.

```java
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
```

---

## 2.3 `global/security/JwtTokenProvider.java`

**설명**: JWT 발급/검증 담당 컴포넌트.
- 생성자에서 secret 으로 HS256 `SecretKey` 생성.
- access/refresh 토큰을 **`type` claim** 으로 구분(access 에는 email 도 포함).
- `parse()` 는 서명/만료를 검증하고 실패 시 jjwt 예외를 던진다(만료는 `ExpiredJwtException`).
- `getUserId/getEmail/isAccessToken/isRefreshToken` 헬퍼 제공.

```java
package com.proj.autodeploy.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증. (HS256)
 * access / refresh 토큰을 "type" claim 으로 구분한다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
        this.refreshTokenValiditySeconds = properties.refreshTokenValiditySeconds();
    }

    public String createAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenValiditySeconds)))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTokenValiditySeconds)))
                .signWith(key)
                .compact();
    }

    /** 서명/만료 검증 후 Claims 반환. 실패 시 jjwt 예외(JwtException 계열)를 던진다. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public long accessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }
}
```

---

## 2.4 `global/security/JwtAuthenticationFilter.java`

**설명**: 매 요청마다 `Authorization: Bearer {token}` 을 파싱해 SecurityContext 에 인증을 채우는 필터(`OncePerRequestFilter`).
- 토큰이 있고 access 타입이면 `AuthPrincipal` 로 `Authentication` 생성 후 컨텍스트에 주입.
- 토큰이 없거나 검증 실패면 **인증을 채우지 않고 통과** → 보호 자원이면 `SecurityConfig` 의 EntryPoint 가 401 처리(필터에서 예외를 던지지 않는 이유: 필터 예외는 `@RestControllerAdvice` 가 못 잡기 때문).

```java
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
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

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
        return null;
    }
}
```

---

## 2.5 `global/config/SecurityConfig.java`

**설명**: Spring Security 필터체인 + CORS + 비밀번호 인코더 설정.
- **stateless**(세션 미사용), CSRF/formLogin/httpBasic/logout 비활성.
- 공개 경로(`/api/auth/**`, `/api/webhooks/**`, swagger, actuator) 외 **전부 인증 필요**.
- `JwtAuthenticationFilter` 를 `UsernamePasswordAuthenticationFilter` 앞에 추가.
- 401(미인증)·403(권한없음)을 **공통 에러 JSON** 으로 직접 응답.
- CORS: 프론트(Vite `:5173`, `:3000`) 허용. 비밀번호는 **BCrypt**.

```java
package com.proj.autodeploy.global.config;

import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.global.security.JwtAuthenticationFilter;
import com.proj.autodeploy.global.security.JwtProperties;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/api/webhooks/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 프론트엔드(Vue/Vite) 로컬 개발 서버에서의 cross-origin 요청 허용.
     * 운영 도메인 확정 시 allowedOrigins 를 설정으로 분리한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider)
            throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.AUTH_TOKEN_INVALID.name(), ErrorCode.AUTH_TOKEN_INVALID.defaultMessage());
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        ErrorCode.PROJECT_ACCESS_DENIED.name(), ErrorCode.PROJECT_ACCESS_DENIED.defaultMessage());
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
        response.getWriter().write(body);
    }
}
```

---

# 3. 요청 처리 흐름 한눈에 보기

**로그인(공개 경로)**
```
POST /api/auth/login
 → SecurityConfig: /api/auth/** 는 permitAll (필터에서 인증 안 채워도 통과)
 → AuthController.login → AuthService.login
 → UserRepository.findByEmail + passwordEncoder.matches
 → JwtTokenProvider.createAccessToken/createRefreshToken
 → TokenResponse 반환
```

**인증 필요한 요청(예: 프로젝트 생성)**
```
POST /api/projects  (Authorization: Bearer xxx)
 → JwtAuthenticationFilter: 토큰 parse → access면 AuthPrincipal 을 SecurityContext 에 주입
 → SecurityConfig: anyRequest authenticated 통과
 → ProjectController(@AuthenticationPrincipal AuthPrincipal) → ProjectService.create(userId, ...)
 → 토큰 없음/위조 시: 인증 미주입 → EntryPoint 가 401 JSON 응답
 → 타인 자원 접근 시: ProjectService 가 ApiException(PROJECT_ACCESS_DENIED) → 403 JSON
```
