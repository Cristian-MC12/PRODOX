package com.prodox.service;

import com.prodox.dto.FactorDto;
import com.prodox.dto.SelectFactorRequest;
import com.prodox.dto.SprintSelectionDto;
import com.prodox.entity.Factor;
import com.prodox.entity.SprintFactorSelection;
import com.prodox.repository.FactorRepository;
import com.prodox.repository.SprintFactorSelectionRepository;
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
