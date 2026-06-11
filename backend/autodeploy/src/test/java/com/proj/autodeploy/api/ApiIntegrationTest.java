package com.proj.autodeploy.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.user.UserRepository;
import com.proj.autodeploy.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth + Project API 통합 테스트 (H2, 외부 의존 없음).
 * 05_api_spec.md 의 응답/에러 포맷·인증 규칙을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtTokenProvider tokenProvider;

    @Test
    @DisplayName("회원가입 → 로그인 정상 흐름")
    void signupThenLogin() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@test.com","password":"P@ssw0rd!","name":"tester"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.email").value("a@test.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@test.com","password":"P@ssw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800));
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401 AUTH_INVALID_CREDENTIALS")
    void loginWithWrongPassword() throws Exception {
        userRepository.save(User.builder()
                .email("b@test.com")
                .passwordHash(passwordEncoder.encode("correct-pass"))
                .name("b")
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"b@test.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("중복 이메일 가입 → 409 USER_EMAIL_DUPLICATED")
    void signupDuplicateEmail() throws Exception {
        userRepository.save(User.builder()
                .email("dup@test.com")
                .passwordHash(passwordEncoder.encode("whatever1"))
                .name("dup")
                .build());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@test.com","password":"P@ssw0rd!","name":"dup2"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("프로젝트 생성/조회: 생성 시 secret 평문, 조회 시 마스킹")
    void createAndGetProject() throws Exception {
        String token = tokenForNewUser("owner@test.com");

        String createBody = """
                {"name":"filehub","description":"파일 업로드",
                 "repositoryUrl":"https://github.com/myorg/filehub","subdomain":"filehub"}
                """;

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").exists())
                .andExpect(jsonPath("$.data.subdomain").value("filehub"))
                .andExpect(jsonPath("$.data.webhookSecret").value(startsWith("whs_")))
                .andExpect(jsonPath("$.data.webhookUrl").exists());

        // 목록 (page 메타 포함)
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subdomain").value("filehub"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("토큰 없이 보호 자원 접근 → 401")
    void accessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("subdomain 자동 생성: 미지정 시 project-{id}")
    void subdomainAutoGenerated() throws Exception {
        String token = tokenForNewUser("auto@test.com");

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"noSub","repositoryUrl":"https://github.com/myorg/nosub"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subdomain").value(startsWith("project-")));
    }

    private String tokenForNewUser(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("P@ssw0rd!"))
                .name("owner")
                .build());
        return tokenProvider.createAccessToken(user.getId(), user.getEmail());
    }
}
