package com.mpdia.repository;

import com.mpdia.entity.CopilotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CopilotConfigRepository extends JpaRepository<CopilotConfig, UUID> {
    Optional<CopilotConfig> findByUserId(String userId);
}
