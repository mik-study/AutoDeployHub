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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

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
