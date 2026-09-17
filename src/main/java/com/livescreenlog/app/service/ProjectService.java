package com.livescreenlog.app.service;

import com.livescreenlog.app.domain.Project;
import com.livescreenlog.app.domain.User;
import com.livescreenlog.app.domain.UserProject;
import com.livescreenlog.app.dto.ProjectCreateRequest;
import com.livescreenlog.app.dto.ProjectDto;
import com.livescreenlog.app.repository.ProjectRepository;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import com.livescreenlog.app.repository.UserProjectRepository;
import com.livescreenlog.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserProjectAccessService userProjectAccessService;
    private final UserRepository userRepository;
    private final UserProjectRepository userProjectRepository;
    private final SessionMetadataRepository sessionMetadataRepository;

    @Transactional(readOnly = true)
    public List<ProjectDto> listAll() {
        List<String> allowed = userProjectAccessService.getAllowedProjectKeys();
        if (allowed != null && allowed.isEmpty()) {
            return List.of();
        }
        List<Project> projects = allowed == null
                ? projectRepository.findAll()
                : projectRepository.findByApiKeyIn(allowed);
        return projects.stream().map(this::toDto).toList();
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

        Project saved = projectRepository.save(project);
        assignCreator(saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        requireManageable(id);
        projectRepository.deleteById(id);
    }

    @Transactional
    public ProjectDto updateSettings(Long id, String mode, String targetUsers) {
        Project project = requireManageable(id);
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

    @Transactional
    public ProjectDto rotateApiKey(Long id) {
        Project project = requireManageable(id);
        String oldKey = project.getApiKey();
        String apiKey = "sl_" + UUID.randomUUID().toString().replace("-", "");
        Project updated = Project.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .apiKey(apiKey)
                .createdAt(project.getCreatedAt())
                .recordingMode(project.getRecordingMode())
                .targetUsers(project.getTargetUsers())
                .build();
        projectRepository.save(updated);
        sessionMetadataRepository.reassignProjectKey(oldKey, apiKey);
        return toDto(updated);
    }

    @Transactional(readOnly = true)
    public boolean isValidApiKey(String apiKey) {
        return projectRepository.existsByApiKey(apiKey);
    }

    private Project requireManageable(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!userProjectAccessService.canManageProject(project.getApiKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return project;
    }

    private void assignCreator(Project project) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return;
        }
        userProjectRepository.save(UserProject.builder()
                .user(user)
                .project(project)
                .roleInProject("OWNER")
                .build());
    }

    private ProjectDto toDto(Project p) {
        String apiKey = p.getApiKey();
        if (!userProjectAccessService.canManageProject(apiKey)) {
            if (apiKey == null || apiKey.length() < 7) {
                apiKey = "sl_••••";
            } else {
                apiKey = apiKey.substring(0, 3) + "••••" + apiKey.substring(apiKey.length() - 4);
            }
        }
        return new ProjectDto(
            p.getId(), 
            p.getName(), 
            p.getDescription(), 
            apiKey, 
            p.getRecordingMode(), 
            p.getTargetUsers(), 
            p.getCreatedAt()
        );
    }
}
