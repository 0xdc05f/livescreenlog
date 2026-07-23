package com.livescreenlog.app.controller;

import com.livescreenlog.app.dto.ProjectCreateRequest;
import com.livescreenlog.app.dto.ProjectDto;
import com.livescreenlog.app.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectDto>> listProjects() {
        return ResponseEntity.ok(projectService.listAll());
    }

    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody ProjectCreateRequest request) {
        try {
            return ResponseEntity.ok(projectService.create(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/settings")
    public ResponseEntity<ProjectDto> updateSettings(
            @PathVariable Long id,
            @RequestBody com.livescreenlog.app.dto.ProjectSettingsRequest request
    ) {
        try {
            return ResponseEntity.ok(projectService.updateSettings(id, request.recordingMode(), request.targetUsers()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
