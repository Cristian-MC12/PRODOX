package com.mpdia.service;

import com.mpdia.dto.FactorDto;
import com.mpdia.dto.SelectFactorRequest;
import com.mpdia.dto.SprintSelectionDto;
import com.mpdia.entity.Factor;
import com.mpdia.entity.SprintFactorSelection;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.SprintFactorSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FactorService {

    private final FactorRepository factorRepository;
    private final SprintFactorSelectionRepository selectionRepository;

    public List<FactorDto> listFactors() {
        return factorRepository.findAllByOrderByNameAsc().stream()
                .map(f -> new FactorDto(f.getId(), f.getName(), f.getDescription(), f.getCategory()))
                .toList();
    }

    public List<SprintSelectionDto> listSelections(String sprintName) {
        return selectionRepository.findBySprintName(sprintName).stream()
                .map(s -> new SprintSelectionDto(s.getId(), s.getFactor().getId(), s.getSprintName()))
                .toList();
    }

    @Transactional
    public void selectFactor(SelectFactorRequest request, String userId) {
        Factor factor = factorRepository.findById(request.factorId())
                .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado."));

        boolean alreadySelected = selectionRepository
                .findByFactor_IdAndSprintName(request.factorId(), request.sprintName())
                .isPresent();

        if (alreadySelected) return;

        SprintFactorSelection selection = new SprintFactorSelection();
        selection.setFactor(factor);
        selection.setSprintName(request.sprintName());
        selection.setSelectedBy(userId);
        selectionRepository.save(selection);
    }

    @Transactional
    public void unselectFactor(UUID factorId, String sprintName) {
        selectionRepository.deleteByFactor_IdAndSprintName(factorId, sprintName);
    }
}
