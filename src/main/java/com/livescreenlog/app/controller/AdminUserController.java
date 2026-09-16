package com.livescreenlog.app.controller;

import com.livescreenlog.app.config.UserRoles;
import com.livescreenlog.app.domain.Project;
import com.livescreenlog.app.domain.User;
import com.livescreenlog.app.domain.UserProject;
import com.livescreenlog.app.domain.UserProjectId;
import com.livescreenlog.app.dto.AssignProjectRequest;
import com.livescreenlog.app.dto.UserCreateRequest;
import com.livescreenlog.app.dto.UserProjectAssignment;
import com.livescreenlog.app.dto.UserSummary;
import com.livescreenlog.app.dto.UserUpdateRequest;
import com.livescreenlog.app.repository.ProjectRepository;
import com.livescreenlog.app.repository.UserProjectRepository;
import com.livescreenlog.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProjectRepository userProjectRepository;
    private final ProjectRepository projectRepository;

    @GetMapping
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> summaries = userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(), u.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserCreateRequest req) {
        if (req.username() == null || req.username().isBlank() ||
            req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
        }
        if (userRepository.existsByUsername(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "username already exists"));
        }
        if (req.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
        }
        String role;
        try {
            role = UserRoles.requireAllowed(req.role());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid role"));
        }
        String hash = passwordEncoder.encode(req.password());
        User user = User.builder()
                .username(req.username())
                .passwordHash(hash)
                .email(req.email())
                .role(role)
                .enabled(true)
                .build();
        User saved = userRepository.save(user);
        UserSummary summary = new UserSummary(saved.getId(), saved.getUsername(), saved.getRole(), saved.isEnabled(), saved.getCreatedAt());
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest req) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (req.role() != null && !req.role().isBlank()) {
            String r = req.role();
            if (!r.equals("SUPER_ADMIN") && !r.equals("ADMIN") && !r.equals("VIEWER")) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid role"));
            }
            user.setRole(r);
        }
        if (req.enabled() != null) {
            if (!req.enabled() && "SUPER_ADMIN".equals(user.getRole())) {
                long enabledSupers = userRepository.findAll().stream()
                        .filter(u -> "SUPER_ADMIN".equals(u.getRole()) && u.isEnabled() && !u.getId().equals(id))
                        .count();
                if (enabledSupers == 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "cannot disable last enabled SUPER_ADMIN"));
                }
            }
            user.setEnabled(req.enabled());
        }
        User saved = userRepository.save(user);
        UserSummary summary = new UserSummary(saved.getId(), saved.getUsername(), saved.getRole(), saved.isEnabled(), saved.getCreatedAt());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}/projects")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listUserProjects(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<UserProject> ups = userProjectRepository.findByUser_Id(id);
        List<UserProjectAssignment> assignments = ups.stream()
                .map(up -> {
                    Long pid = (up.getProject() != null) ? up.getProject().getId() : null;
                    String pname = (up.getProject() != null) ? up.getProject().getName() : "";
                    return new UserProjectAssignment(id, pid, pname, up.getRoleInProject());
                })
                .toList();
        return ResponseEntity.ok(assignments);
    }

    @PostMapping("/{id}/projects")
    @Transactional
    public ResponseEntity<?> assignProject(@PathVariable Long id, @RequestBody AssignProjectRequest req) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        Project project = null;
        if (req.projectId() != null) {
            project = projectRepository.findById(req.projectId()).orElse(null);
        } else if (req.apiKey() != null && !req.apiKey().isBlank()) {
            project = projectRepository.findByApiKey(req.apiKey()).orElse(null);
        }
        if (project == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "project not found"));
        }
        final Long pid = project.getId();
        String role = (req.roleInProject() == null || req.roleInProject().isBlank()) ? "VIEWER" : req.roleInProject().toUpperCase();
        if (!role.equals("OWNER") && !role.equals("ADMIN") && !role.equals("VIEWER")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid roleInProject"));
        }
        // prevent duplicate
        boolean exists = userProjectRepository.findByUser_Id(id).stream()
                .anyMatch(up -> up.getProject() != null && up.getProject().getId().equals(pid));
        if (exists) {
            return ResponseEntity.badRequest().body(Map.of("error", "user already assigned to this project"));
        }
        UserProject up = UserProject.builder()
                .user(user)
                .project(project)
                .roleInProject(role)
                .build();
        userProjectRepository.save(up);
        UserProjectAssignment assignment = new UserProjectAssignment(id, project.getId(), project.getName(), role);
        return ResponseEntity.ok(assignment);
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ResponseEntity<?> removeProjectAssignment(@PathVariable Long id, @PathVariable Long projectId) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        UserProjectId upid = new UserProjectId(id, projectId);
        if (!userProjectRepository.existsById(upid)) {
            return ResponseEntity.notFound().build();
        }
        userProjectRepository.deleteById(upid);
        return ResponseEntity.noContent().build();
    }
}
