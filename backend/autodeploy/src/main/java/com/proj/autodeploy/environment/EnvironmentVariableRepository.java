package com.proj.autodeploy.environment;

import com.proj.autodeploy.environment.domain.EnvironmentVariable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Long> {

    List<EnvironmentVariable> findByProjectId(Long projectId);

    Optional<EnvironmentVariable> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndKey(Long projectId, String key);
}
