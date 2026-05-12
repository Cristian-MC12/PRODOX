package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "copilot_config")
@Getter @Setter @NoArgsConstructor
public class CopilotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String tool = "jira"; // jira | github

    @Column(nullable = false)
    private String url;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String frequency = "daily"; // hourly | every_6h | daily | weekly

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
