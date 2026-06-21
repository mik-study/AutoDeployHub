package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.Deployment;
import com.proj.autodeploy.deployment.domain.DeploymentStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Page<Deployment> findByProjectId(Long projectId, Pageable pageable);

    /** 프로젝트에 진행 중(in-progress) 배포가 있는지. (중복 배포 가드용) */
    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<DeploymentStatus> statuses);
}
