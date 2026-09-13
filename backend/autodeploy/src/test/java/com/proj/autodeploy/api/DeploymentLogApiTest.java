package com.proj.autodeploy.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.proj.autodeploy.deployment.DeploymentLogService;
import com.proj.autodeploy.deployment.DeploymentRepository;
import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import com.proj.autodeploy.deployment.domain.LogLevel;
import com.proj.autodeploy.global.security.JwtTokenProvider;
import com.proj.autodeploy.user.UserRepository;
import com.proj.autodeploy.user.domain.User;
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
 * 배포 로그 스냅샷 API 통합 테스트. (05_api_spec.md §4.6, 과제 4)
 *
 * <p>SSE 스트림은 비동기 응답이라 MockMvc 로 끝까지 검증하기 번거롭다 —
 * 스트림 동작은 {@code DeploymentSseRegistryTest} 와 {@code DeploymentLogStreamTest} 가 담당한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeploymentLogApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentLogService deploymentLogService;

    @Test
    @DisplayName("스냅샷 응답이 §4.6 포맷(data/nextFromSequence/hasMore)을 지킨다")
    void snapshotFormat() throws Exception {
        String auth = "Bearer " + tokenForNewUser("log-a@test.com");
        long projectId = createProject(auth);
        long deploymentId = saveQueuedDeployment(projectId);

        deploymentLogService.info(deploymentId, "Cloning repository...");
        deploymentLogService.info(deploymentId, "Dockerfile validated");
        deploymentLogService.append(deploymentId, LogLevel.ERROR, "build failed");

        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[0].level").value("INFO"))
                .andExpect(jsonPath("$.data[0].message").value("Cloning repository..."))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[2].level").value("ERROR"))
                .andExpect(jsonPath("$.nextFromSequence").value(4))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("limit 을 넘으면 hasMore=true 이고 nextFromSequence 로 이어받을 수 있다")
    void snapshotPaging() throws Exception {
        String auth = "Bearer " + tokenForNewUser("log-b@test.com");
        long projectId = createProject(auth);
        long deploymentId = saveQueuedDeployment(projectId);

        for (int i = 1; i <= 5; i++) {
            deploymentLogService.info(deploymentId, "line " + i);
        }

        // 첫 페이지: 2건 + hasMore
        String first = mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextFromSequence").value(3))
                .andReturn().getResponse().getContentAsString();
        int next = ((Number) JsonPath.read(first, "$.nextFromSequence")).intValue();

        // 이어받기: 남은 3건, hasMore=false
        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .param("fromSequence", String.valueOf(next))
                        .param("limit", "200")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].sequence").value(3))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextFromSequence").value(6));
    }

    @Test
    @DisplayName("로그가 없으면 빈 배열 + nextFromSequence 는 요청한 지점 그대로")
    void snapshotEmpty() throws Exception {
        String auth = "Bearer " + tokenForNewUser("log-c@test.com");
        long projectId = createProject(auth);
        long deploymentId = saveQueuedDeployment(projectId);

        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .param("fromSequence", "7")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextFromSequence").value(7));
    }

    @Test
    @DisplayName("sequence 는 1부터 연속으로 매겨진다")
    void sequenceIsContiguous() throws Exception {
        String auth = "Bearer " + tokenForNewUser("log-d@test.com");
        long projectId = createProject(auth);
        long deploymentId = saveQueuedDeployment(projectId);

        for (int i = 0; i < 4; i++) {
            deploymentLogService.info(deploymentId, "m" + i);
        }

        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[1].sequence").value(2))
                .andExpect(jsonPath("$.data[2].sequence").value(3))
                .andExpect(jsonPath("$.data[3].sequence").value(4));
    }

    @Test
    @DisplayName("타인의 배포 로그는 조회할 수 없다 (403)")
    void cannotReadOthersLogs() throws Exception {
        String ownerAuth = "Bearer " + tokenForNewUser("log-owner@test.com");
        long projectId = createProject(ownerAuth);
        long deploymentId = saveQueuedDeployment(projectId);
        String strangerAuth = "Bearer " + tokenForNewUser("log-stranger@test.com");

        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .header(HttpHeaders.AUTHORIZATION, strangerAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 배포의 로그 조회는 404")
    void missingDeploymentIs404() throws Exception {
        String auth = "Bearer " + tokenForNewUser("log-404@test.com");

        mockMvc.perform(get("/api/deployments/{id}/logs", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("SSE 스트림은 Authorization 헤더 없이 access_token 쿼리 파라미터로도 인증된다")
    void streamAcceptsQueryParamToken() throws Exception {
        String token = tokenForNewUser("log-sse@test.com");
        long projectId = createProject("Bearer " + token);
        long deploymentId = saveQueuedDeployment(projectId);

        mockMvc.perform(get("/api/deployments/{id}/logs/stream", deploymentId)
                        .param("access_token", token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 API 는 access_token 쿼리 파라미터를 받지 않는다 (SSE 경로 한정)")
    void queryParamTokenIsRejectedOnNormalApi() throws Exception {
        String token = tokenForNewUser("log-noquery@test.com");
        long projectId = createProject("Bearer " + token);
        long deploymentId = saveQueuedDeployment(projectId);

        mockMvc.perform(get("/api/deployments/{id}/logs", deploymentId)
                        .param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------

    private long saveQueuedDeployment(long projectId) {
        Deployment deployment = Deployment.builder()
                .projectId(projectId)
                .branch("main")
                .build();
        deployment.transitionTo(DeploymentStatus.QUEUED);
        return deploymentRepository.save(deployment).getId();
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
