package com.mpdia.repository;

import com.mpdia.entity.SprintFactorSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintFactorSelectionRepository extends JpaRepository<SprintFactorSelection, UUID> {
    List<SprintFactorSelection> findBySprintName(String sprintName);
    Optional<SprintFactorSelection> findByFactor_IdAndSprintName(UUID factorId, String sprintName);
    void deleteByFactor_IdAndSprintName(UUID factorId, String sprintName);
}
