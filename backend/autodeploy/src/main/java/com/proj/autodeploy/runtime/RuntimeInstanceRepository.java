package com.proj.autodeploy.runtime;

import com.proj.autodeploy.runtime.domain.RuntimeInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeInstanceRepository extends JpaRepository<RuntimeInstance, Long> {

    List<RuntimeInstance> findByProjectId(Long projectId);

    Optional<RuntimeInstance> findByProjectIdAndActiveTrue(Long projectId);
}
