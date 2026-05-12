package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_factor_selections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"factor_id", "sprint_name"}))
@Getter @Setter @NoArgsConstructor
public class SprintFactorSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factor_id", nullable = false)
    private Factor factor;

    @Column(name = "sprint_name", nullable = false)
    private String sprintName = "Sprint Actual";

    @Column(name = "selected_by", nullable = false)
    private String selectedBy;

    @Column(name = "selected_at", nullable = false, updatable = false)
    private Instant selectedAt = Instant.now();
}
