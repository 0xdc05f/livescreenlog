package com.livescreenlog.app.service;

import com.livescreenlog.app.domain.Project;
import com.livescreenlog.app.dto.ProjectCreateRequest;
import com.livescreenlog.app.dto.ProjectDto;
import com.livescreenlog.app.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectDto> listAll() {
        return projectRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ProjectDto create(ProjectCreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }

        // Generate a unique API key: sl_ prefix + UUID without hyphens
        String apiKey = "sl_" + UUID.randomUUID().toString().replace("-", "");

        Project project = Project.builder()
                .name(req.name().trim())
                .description(req.description())
                .apiKey(apiKey)
                .build();

        return toDto(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }

    @Transactional
    public ProjectDto updateSettings(Long id, String mode, String targetUsers) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        Project updated = Project.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .apiKey(project.getApiKey())
                .createdAt(project.getCreatedAt())
                .recordingMode(mode != null ? mode.trim() : "ALL")
                .targetUsers(targetUsers != null ? targetUsers.trim() : null)
                .build();
        return toDto(projectRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public boolean isValidApiKey(String apiKey) {
        return projectRepository.existsByApiKey(apiKey);
    }

    private ProjectDto toDto(Project p) {
        return new ProjectDto(
            p.getId(), 
            p.getName(), 
            p.getDescription(), 
            p.getApiKey(), 
            p.getRecordingMode(), 
            p.getTargetUsers(), 
            p.getCreatedAt()
        );
    }
}
