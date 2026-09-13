package com.proj.autodeploy.runtime;

import com.proj.autodeploy.runtime.domain.RuntimeInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeInstanceRepository extends JpaRepository<RuntimeInstance, Long> {

    List<RuntimeInstance> findByProjectId(Long projectId);

    Optional<RuntimeInstance> findByProjectIdAndActiveTrue(Long projectId);

    /** 한 배포가 띄운 인스턴스. 4주차는 배포당 BLUE 하나라 단건이다. (과제 3 실패 처리) */
    Optional<RuntimeInstance> findByDeploymentId(Long deploymentId);
}
