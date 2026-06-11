package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.DeploymentLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {

    List<DeploymentLog> findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(
            Long deploymentId, Long fromSequence);
}
