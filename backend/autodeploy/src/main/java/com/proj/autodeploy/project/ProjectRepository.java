package com.proj.autodeploy.project;

import com.proj.autodeploy.project.domain.Project;
import com.proj.autodeploy.project.domain.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerIdAndStatus(Long ownerId, ProjectStatus status, Pageable pageable);

    boolean existsBySubdomain(String subdomain);
}
