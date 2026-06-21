package com.proj.autodeploy.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * Deployment API 통합 테스트 (H2). 과제 1 완료 판정. (가이드 §7)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeploymentApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    @DisplayName("배포 생성 → 중복 409 → 이력 → 단건 → 취소 E2E")
    void deploymentLifecycle() throws Exception {
        String token = tokenForNewUser("deployer@test.com");
        String auth = "Bearer " + token;
        long projectId = createProject(auth);

        // 1) 배포 요청 → 202, QUEUED
        String createBody = mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.deploymentId").exists())
                .andExpect(jsonPath("$.data.projectId").value((int) projectId))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        long deploymentId = ((Number) JsonPath.read(createBody, "$.data.deploymentId")).longValue();

        // 2) 같은 프로젝트 재요청 → 409 (진행 중 배포 존재)
        mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_ALREADY_IN_PROGRESS"));

        // 3) 이력 → 1건
        mockMvc.perform(get("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].status").value("QUEUED"));

        // 4) 단건 조회
        mockMvc.perform(get("/api/deployments/{id}", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deploymentId").value((int) deploymentId))
                .andExpect(jsonPath("$.data.projectId").value((int) projectId));

        // 5) 취소 → 200, CANCELED
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("진행 단계가 아닌 배포 취소 시 422")
    void cancelNonCancelable() throws Exception {
        String token = tokenForNewUser("deployer2@test.com");
        String auth = "Bearer " + token;
        long projectId = createProject(auth);

        String body = mockMvc.perform(post("/api/projects/{id}/deployments", projectId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long deploymentId = ((Number) JsonPath.read(body, "$.data.deploymentId")).longValue();

        // 첫 취소 → CANCELED
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        // 이미 CANCELED(취소 불가 상태) → 422
        mockMvc.perform(post("/api/deployments/{id}/cancel", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_NOT_CANCELABLE"));
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
