package com.proj.autodeploy.environment.dto;

import com.proj.autodeploy.environment.domain.EnvironmentVariable;
import java.time.Instant;

/**
 * 환경변수 등록/수정 응답. (05_api_spec.md §3.1, §3.3)
 *
 * <p><b>value 를 담지 않는다.</b> 명세가 그렇게 정해져 있고, 방금 보낸 값을 그대로 돌려줄 이유도 없다.
 * 값을 확인하려면 목록({@link EnvListItemResponse})을 쓰면 되고, secret 은 거기서도 마스킹된다.
 */
public record EnvResponse(
        Long envId,
        Long projectId,
        String key,
        boolean isSecret,
        Instant createdAt
) {

    public static EnvResponse from(EnvironmentVariable env) {
        return new EnvResponse(
                env.getId(),
                env.getProjectId(),
                env.getKey(),
                env.isSecret(),
                env.getCreatedAt()
        );
    }
}
