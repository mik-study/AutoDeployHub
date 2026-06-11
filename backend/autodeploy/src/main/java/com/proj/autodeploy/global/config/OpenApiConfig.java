package com.proj.autodeploy.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 에 JWT Bearer 인증("Authorize" 자물쇠 버튼)을 노출한다.
 *
 * <p>{@code @SecurityScheme} 는 클래스 레벨 애너테이션으로, springdoc 이 스캔하는
 * 설정 클래스에 한 번만 선언하면 OpenAPI 문서에 보안 스킴이 등록된다.
 * {@code @OpenAPIDefinition(security = ...)} 로 전역 적용하면 모든 엔드포인트에
 * Authorize 토큰이 함께 전송된다(공개 엔드포인트는 서버가 그냥 허용하므로 영향 없음).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "AutoDeployHub API", version = "v0.0.1"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 응답의 accessToken 을 입력하세요. (Bearer 접두사는 자동으로 붙음)"
)
public class OpenApiConfig {
}
