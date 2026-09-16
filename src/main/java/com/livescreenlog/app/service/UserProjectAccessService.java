package com.livescreenlog.app.service;

import com.livescreenlog.app.domain.SessionMetadata;
import com.livescreenlog.app.domain.User;
import com.livescreenlog.app.domain.UserProject;
import com.livescreenlog.app.repository.SessionMetadataRepository;
import com.livescreenlog.app.repository.UserProjectRepository;
import com.livescreenlog.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserProjectAccessService {

    private final UserRepository userRepository;
    private final UserProjectRepository userProjectRepository;
    private final SessionMetadataRepository sessionMetadataRepository;

    public List<String> getAllowedProjectKeys() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return List.of();
        }
        boolean sessionOnly = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SESSION".equals(a.getAuthority()));
        if (sessionOnly) {
            return List.of();
        }
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (isSuperAdmin) {
            return null;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return List.of();
        }
        List<UserProject> ups = userProjectRepository.findByUser_Id(user.getId());
        return ups.stream()
                .map(up -> up.getProject() != null ? up.getProject().getApiKey() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public boolean hasAccessToProject(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return false;
        }
        List<String> allowed = getAllowedProjectKeys();
        if (allowed == null) {
            return true;
        }
        return allowed.contains(projectKey);
    }

    public boolean hasAccessToSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return sessionMetadataRepository.findById(sessionId)
                .map(SessionMetadata::getProjectKey)
                .map(this::hasAccessToProject)
                .orElse(false);
    }

    public boolean canManageProject(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return false;
        }
        return userProjectRepository.findByUser_IdAndProject_ApiKey(user.getId(), projectKey)
                .map(UserProject::getRoleInProject)
                .map(role -> "OWNER".equals(role) || "ADMIN".equals(role))
                .orElse(false);
    }

    public boolean canManageSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return sessionMetadataRepository.findById(sessionId)
                .map(SessionMetadata::getProjectKey)
                .map(this::canManageProject)
                .orElse(false);
    }
}
