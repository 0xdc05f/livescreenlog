package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByApiKey(String apiKey);
    boolean existsByApiKey(String apiKey);
}
