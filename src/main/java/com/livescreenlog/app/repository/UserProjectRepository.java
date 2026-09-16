package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.UserProject;
import com.livescreenlog.app.domain.UserProjectId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProjectRepository extends JpaRepository<UserProject, UserProjectId> {
    List<UserProject> findByUser_Id(Long userId);
    List<UserProject> findByProject_Id(Long projectId);
    Optional<UserProject> findByUser_IdAndProject_ApiKey(Long userId, String apiKey);
}
