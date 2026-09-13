package com.proj.autodeploy.environment;

import com.proj.autodeploy.environment.crypto.EnvCrypto;
import com.proj.autodeploy.environment.domain.EnvironmentVariable;
import com.proj.autodeploy.environment.dto.CreateEnvRequest;
import com.proj.autodeploy.environment.dto.EnvListItemResponse;
import com.proj.autodeploy.environment.dto.EnvResponse;
import com.proj.autodeploy.environment.dto.UpdateEnvRequest;
import com.proj.autodeploy.global.error.ApiException;
import com.proj.autodeploy.global.error.ErrorCode;
import com.proj.autodeploy.project.ProjectService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환경변수 CRUD + 암복호화 + 마스킹. (05_api_spec.md §3)
 *
 * <p>외부 API 경로는 전부 {@link ProjectService#getOwnedProject} 로 소유권을 먼저 검증한다.
 * Worker 주입용 {@link #resolveForRuntime(Long)} 만 사용자 검증이 없는데, 이건 HTTP 로 노출되지 않는
 * 내부 메서드다 — 아래 주석 참고.
 */
@Service
@RequiredArgsConstructor
public class EnvironmentVariableService {

    private final EnvironmentVariableRepository environmentVariableRepository;
    private final ProjectService projectService;
    private final EnvCrypto envCrypto;

    /** §3.1 등록 → 201. */
    @Transactional
    public EnvResponse create(Long userId, Long projectId, CreateEnvRequest request) {
        projectService.getOwnedProject(userId, projectId);

        // DB 유니크 제약(uk_env_project_key)이 최후 방어선이지만, 명세의 409 를 정확히 던지려면
        // 여기서 먼저 확인해야 한다. 제약 위반 예외는 500 으로 새어나간다.
        if (environmentVariableRepository.existsByProjectIdAndKey(projectId, request.key())) {
            throw ApiException.of(ErrorCode.ENV_KEY_DUPLICATED, "key", request.key());
        }

        EnvironmentVariable env = EnvironmentVariable.builder()
                .projectId(projectId)
                .key(request.key())
                .encryptedValue(envCrypto.encrypt(request.value()))
                .secret(request.secretOrDefault())
                .build();
        return EnvResponse.from(environmentVariableRepository.save(env));
    }

    /** §3.2 목록 → 200. secret 은 마스킹, 일반 값은 복호화해 내려준다. */
    @Transactional(readOnly = true)
    public List<EnvListItemResponse> list(Long userId, Long projectId) {
        projectService.getOwnedProject(userId, projectId);

        return environmentVariableRepository.findByProjectId(projectId).stream()
                .map(env -> new EnvListItemResponse(
                        env.getId(),
                        env.getKey(),
                        env.isSecret() ? EnvListItemResponse.MASKED : envCrypto.decrypt(env.getEncryptedValue()),
                        env.isSecret(),
                        env.getUpdatedAt()))
                .toList();
    }

    /** §3.3 값 수정 → 200. */
    @Transactional
    public EnvResponse update(Long userId, Long projectId, Long envId, UpdateEnvRequest request) {
        projectService.getOwnedProject(userId, projectId);

        EnvironmentVariable env = getOwnedEnv(projectId, envId);
        env.changeValue(envCrypto.encrypt(request.value()));
        return EnvResponse.from(env);
    }

    /** §3.4 삭제 → 204. */
    @Transactional
    public void delete(Long userId, Long projectId, Long envId) {
        projectService.getOwnedProject(userId, projectId);
        environmentVariableRepository.delete(getOwnedEnv(projectId, envId));
    }

    /**
     * <b>Worker 전용</b> — 컨테이너 주입용 복호화 값. (과제 3 {@code docker run -e KEY=VALUE})
     *
     * <p>외부 GET({@link #list})과 일부러 분리했다. list 는 secret 을 마스킹하지만 이건 전부 평문으로
     * 돌려주므로, HTTP 로 노출되는 경로에서는 절대 호출하면 안 된다. 사용자 검증이 없는 것도
     * 워커 경로에는 사용자가 없기 때문이다.
     *
     * <p>반환값을 로그에 찍지 말 것.
     *
     * @return key → 복호화된 value. 등록된 게 없으면 빈 맵
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolveForRuntime(Long projectId) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (EnvironmentVariable env : environmentVariableRepository.findByProjectId(projectId)) {
            resolved.put(env.getKey(), envCrypto.decrypt(env.getEncryptedValue()));
        }
        return resolved;
    }

    private EnvironmentVariable getOwnedEnv(Long projectId, Long envId) {
        return environmentVariableRepository.findByIdAndProjectId(envId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ENV_NOT_FOUND, "envId", envId));
    }
}
