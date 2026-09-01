package com.livescreenlog.app.repository;

import com.livescreenlog.app.domain.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerConfigRepository extends JpaRepository<ServerConfig, String> {
}
