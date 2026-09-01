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

/**
 * Centralized service for project and session access control.
 *
 * <p>Determines the set of projects the currently authenticated user may access,
 * using Spring Security context and the user_projects mapping.
 *
 * <p>Access rules:
 * <ul>
 *   <li>Super admins (ROLE_SUPER_ADMIN) and unauthenticated contexts receive null,
 *       meaning "no filter" (see all projects/sessions).</li>
 *   <li>Authenticated non-admin users receive the list of project API keys from their
 *       UserProject assignments (empty list if none).</li>
 *   <li>Missing user record yields empty list (deny all).</li>
 * </ul>
 *
 * <p>This service is intended to be used by read services to scope queries and
 * by detail/event methods to enforce per-resource checks without leaking existence.
 */
@Service
@RequiredArgsConstructor
public class UserProjectAccessService {

    private final UserRepository userRepository;
    private final UserProjectRepository userProjectRepository;
    private final SessionMetadataRepository sessionMetadataRepository;

    /**
     * Extracts the allowed project API keys for the current authentication.
     *
     * @return null to indicate no filter should be applied (super admin sees all),
     *         empty list to deny all access,
     *         or a distinct list of project apiKeys the user is permitted to see.
     */
    public List<String> getAllowedProjectKeys() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            String username = authentication.getName();
            boolean isSuperAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
            if (isSuperAdmin) {
                return null;  // no additional filter
            } else {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    List<UserProject> ups = userProjectRepository.findByUser_Id(user.getId());
                    return ups.stream()
                            .map(up -> up.getProject() != null ? up.getProject().getApiKey() : null)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                } else {
                    return List.of();  // no matching user -> no access
                }
            }
        } else {
            return null;
        }
    }

    /**
     * Returns whether the current user has access to the given project key.
     * <p>Null allowed set (super admin) grants access to everything.
     *
     * @param projectKey project apiKey to check
     * @return true if access is permitted
     */
    public boolean hasAccessToProject(String projectKey) {
        List<String> allowed = getAllowedProjectKeys();
        if (allowed == null) {
            return true;
        }
        return allowed.contains(projectKey);
    }

    /**
     * Returns whether the current user has access to the session.
     * <p>Resolves the session's projectKey via SessionMetadataRepository then delegates
     * to project access check. Returns false for unknown sessions (no leak of existence).
     *
     * @param sessionId the session identifier
     * @return true if the user may access the session's data
     */
    public boolean hasAccessToSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return sessionMetadataRepository.findById(sessionId)
                .map(SessionMetadata::getProjectKey)
                .map(this::hasAccessToProject)
                .orElse(false);
    }
}
