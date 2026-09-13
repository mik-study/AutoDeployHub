package com.proj.autodeploy.deployment;

import com.proj.autodeploy.deployment.domain.DeploymentLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {

    /** SSE 재연결 백필용 - 개수 제한 없이 fromSequence 이후 전부. */
    List<DeploymentLog> findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(
            Long deploymentId, Long fromSequence);

    /** 스냅샷 페이징용. limit+1 개를 요청해 hasMore 를 판정한다. (05_api_spec.md §4.6) */
    List<DeploymentLog> findByDeploymentIdAndSequenceGreaterThanEqualOrderBySequenceAsc(
            Long deploymentId, Long fromSequence, Pageable pageable);

    /** 다음 sequence 를 계산하기 위한 현재 최대값. */
    Optional<DeploymentLog> findTopByDeploymentIdOrderBySequenceDesc(Long deploymentId);
}
