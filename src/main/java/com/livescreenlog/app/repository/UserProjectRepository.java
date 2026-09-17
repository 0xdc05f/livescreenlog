package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.UserProject;
import com.livescreenlog.app.domain.UserProjectId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProjectRepository extends JpaRepository<UserProject, UserProjectId> {
    @Query("select up from UserProject up join fetch up.project where up.user.id = :userId")
    List<UserProject> findByUser_Id(@Param("userId") Long userId);
    List<UserProject> findByProject_Id(Long projectId);
    @Query("select up from UserProject up join fetch up.project where up.user.id = :userId and up.project.apiKey = :apiKey")
    Optional<UserProject> findByUser_IdAndProject_ApiKey(@Param("userId") Long userId, @Param("apiKey") String apiKey);
}
