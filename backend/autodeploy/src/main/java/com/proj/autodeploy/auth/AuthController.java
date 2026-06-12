package com.proj.autodeploy.auth;

import com.proj.autodeploy.auth.dto.LoginRequest;
import com.proj.autodeploy.auth.dto.RefreshRequest;
import com.proj.autodeploy.auth.dto.SignupRequest;
import com.proj.autodeploy.auth.dto.SignupResponse;
import com.proj.autodeploy.auth.dto.TokenResponse;
import com.proj.autodeploy.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    // TODO : 로그인 응답값 추가할 요소 생각해보기
    // 이메일/이름 정도 일단은
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // 무상태(stateless) JWT: 서버 저장소가 없으므로 현재는 클라이언트가 토큰을 폐기한다.
        // TODO: 서버측 토큰 무효화 구현 (Redis 도입 후 refresh 토큰 blacklist / 토큰 회전)
        return ResponseEntity.noContent().build();
    }
}
