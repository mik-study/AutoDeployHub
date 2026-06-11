package com.proj.autodeploy.project;

import com.proj.autodeploy.global.response.ApiResponse;
import com.proj.autodeploy.global.response.PagedResponse;
import com.proj.autodeploy.global.security.AuthPrincipal;
import com.proj.autodeploy.project.dto.CreateProjectRequest;
import com.proj.autodeploy.project.dto.CreateProjectResponse;
import com.proj.autodeploy.project.dto.ProjectResponse;
import com.proj.autodeploy.project.dto.ProjectSummaryResponse;
import com.proj.autodeploy.project.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateProjectResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateProjectRequest request) {
        CreateProjectResponse response = projectService.create(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProjectSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProjectSummaryResponse> page = projectService.list(principal.userId(), pageable);
        return ResponseEntity.ok(PagedResponse.of(page));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.of(projectService.get(principal.userId(), projectId)));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.of(projectService.update(principal.userId(), projectId, request)));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long projectId) {
        projectService.delete(principal.userId(), projectId);
        return ResponseEntity.noContent().build();
    }
}
