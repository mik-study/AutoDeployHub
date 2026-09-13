package com.proj.autodeploy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.proj.autodeploy.environment.EnvironmentVariableRepository;
import com.proj.autodeploy.environment.EnvironmentVariableService;
import com.proj.autodeploy.environment.domain.EnvironmentVariable;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.user.UserRepository;
import com.proj.autodeploy.user.domain.User;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * EnvironmentVariable API 통합 테스트 (H2). 과제 6 완료 판정. (가이드 §6)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnvironmentApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EnvironmentVariableRepository environmentVariableRepository;
    @Autowired EnvironmentVariableService environmentVariableService;

    @Test
    @DisplayName("등록 → 중복 409 → 목록(마스킹) → 수정 → 삭제 E2E")
    void environmentVariableLifecycle() throws Exception {
        String auth = "Bearer " + tokenForNewUser("env-owner@test.com");
        long projectId = createProject(auth);

        // 1) secret 등록 → 201. 응답에 value 가 없어야 한다 (명세 §3.1)
        String createBody = mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"DATABASE_URL","value":"postgres://secret","isSecret":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.envId").exists())
                .andExpect(jsonPath("$.data.projectId").value((int) projectId))
                .andExpect(jsonPath("$.data.key").value("DATABASE_URL"))
                .andExpect(jsonPath("$.data.isSecret").value(true))
                .andExpect(jsonPath("$.data.value").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long envId = ((Number) JsonPath.read(createBody, "$.data.envId")).longValue();

        // 2) 일반(non-secret) 등록 → 201
        mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"APP_PORT","value":"8080","isSecret":false}
                                """))
                .andExpect(status().isCreated());

        // 3) 같은 key 재등록 → 409
        mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"DATABASE_URL","value":"other","isSecret":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENV_KEY_DUPLICATED"));

        // 4) 목록 → secret 은 ****, 일반은 실제 값 (명세 §3.2)
        mockMvc.perform(get("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.key=='DATABASE_URL')].value").value("****"))
                .andExpect(jsonPath("$.data[?(@.key=='APP_PORT')].value").value("8080"));

        // 5) 수정 → 200
        mockMvc.perform(patch("/api/projects/{pid}/env/{eid}", projectId, envId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"postgres://updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("DATABASE_URL"));

        // 수정된 값이 실제로 반영됐는지는 Worker 주입 경로로 확인 (마스킹 우회)
        assertThat(environmentVariableService.resolveForRuntime(projectId))
                .containsEntry("DATABASE_URL", "postgres://updated")
                .containsEntry("APP_PORT", "8080");

        // 6) 삭제 → 204, 이후 재삭제는 404
        mockMvc.perform(delete("/api/projects/{pid}/env/{eid}", projectId, envId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/projects/{pid}/env/{eid}", projectId, envId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENV_NOT_FOUND"));
    }

    @Test
    @DisplayName("DB 에는 평문이 저장되지 않는다")
    void valueIsStoredEncrypted() throws Exception {
        String auth = "Bearer " + tokenForNewUser("env-crypto@test.com");
        long projectId = createProject(auth);

        mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"TOKEN","value":"plain-text-secret","isSecret":true}
                                """))
                .andExpect(status().isCreated());

        EnvironmentVariable stored = environmentVariableRepository.findByProjectId(projectId).getFirst();
        assertThat(stored.getEncryptedValue())
                .isNotEqualTo("plain-text-secret")
                .doesNotContain("plain-text-secret");
    }

    @Test
    @DisplayName("타인의 프로젝트 환경변수에는 접근할 수 없다 (403)")
    void cannotAccessOthersProject() throws Exception {
        String ownerAuth = "Bearer " + tokenForNewUser("env-a@test.com");
        long projectId = createProject(ownerAuth);
        String strangerAuth = "Bearer " + tokenForNewUser("env-b@test.com");

        mockMvc.perform(get("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, strangerAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROJECT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("key 형식이 올바르지 않으면 400")
    void rejectsInvalidKey() throws Exception {
        String auth = "Bearer " + tokenForNewUser("env-validation@test.com");
        long projectId = createProject(auth);

        mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"1-invalid key","value":"v","isSecret":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Worker 주입용 조회는 secret 도 복호화된 평문을 돌려준다")
    void resolveForRuntimeReturnsPlainValues() throws Exception {
        String auth = "Bearer " + tokenForNewUser("env-runtime@test.com");
        long projectId = createProject(auth);

        mockMvc.perform(post("/api/projects/{id}/env", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"SECRET_KEY","value":"super-secret","isSecret":true}
                                """))
                .andExpect(status().isCreated());

        Map<String, String> resolved = environmentVariableService.resolveForRuntime(projectId);

        assertThat(resolved).containsEntry("SECRET_KEY", "super-secret");
    }

    private long createProject(String auth) throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"demo","repositoryUrl":"https://github.com/myorg/demo"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.projectId")).longValue();
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
