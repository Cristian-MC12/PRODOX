package com.mpdia.service;

import com.mpdia.dto.CreateIndicatorRequest;
import com.mpdia.dto.GenerateIndicatorsRequest;
import com.mpdia.dto.IndicatorDto;
import com.mpdia.dto.RejectIndicatorRequest;
import com.mpdia.entity.Factor;
import com.mpdia.entity.Indicator;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.IndicatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final IndicatorRepository indicatorRepository;
    private final FactorRepository factorRepository;

    // Valores simulados por categoría de factor (RF07 — generación automática del Copiloto)
    private static final Map<String, double[]> SEED_VALUES = Map.of(
        "Productividad", new double[]{72.0, 85.5, 91.0},
        "Calidad",       new double[]{68.0, 74.5, 88.0},
        "Cumplimiento",  new double[]{80.0, 90.0, 95.5}
    );

    private static final Map<String, String> CATEGORY_UNIT = Map.of(
        "Productividad", "pts",
        "Calidad",       "%",
        "Cumplimiento",  "%"
    );

    public List<IndicatorDto> listAll() {
        return indicatorRepository.findAllByOrderByMeasuredAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public IndicatorDto create(CreateIndicatorRequest request) {
        Factor factor = factorRepository.findById(request.factorId())
                .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado."));
        Indicator indicator = new Indicator();
        indicator.setFactor(factor);
        indicator.setValue(request.value());
        indicator.setUnit(request.unit());
        return toDto(indicatorRepository.save(indicator));
    }

    /**
     * RF07 — El Copiloto genera automáticamente métricas para un factor seleccionado.
     * Simula 3 valores representativos según la categoría del factor.
     */
    @Transactional
    public List<IndicatorDto> generateForFactor(GenerateIndicatorsRequest request) {
        Factor factor = factorRepository.findById(request.factorId())
                .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado."));

        double[] values = SEED_VALUES.getOrDefault(factor.getCategory(), new double[]{70.0, 80.0, 90.0});
        String unit     = CATEGORY_UNIT.getOrDefault(factor.getCategory(), "");

        return java.util.Arrays.stream(values).mapToObj(v -> {
            Indicator ind = new Indicator();
            ind.setFactor(factor);
            ind.setValue(v);
            ind.setUnit(unit);
            return toDto(indicatorRepository.save(ind));
        }).toList();
    }

    /** RF09 — Aprobar métrica */
    @Transactional
    public IndicatorDto approve(UUID id, String userId) {
        Indicator indicator = findOrThrow(id);
        if ("aprobado".equals(indicator.getStatus())) {
            throw new IllegalStateException("El indicador ya fue aprobado.");
        }
        indicator.setStatus("aprobado");
        indicator.setApprovedBy(userId);
        indicator.setApprovedAt(Instant.now());
        return toDto(indicatorRepository.save(indicator));
    }

    /** RF11 — Rechazar métrica con motivo */
    @Transactional
    public IndicatorDto reject(UUID id, String userId, RejectIndicatorRequest request) {
        Indicator indicator = findOrThrow(id);
        if ("aprobado".equals(indicator.getStatus())) {
            throw new IllegalStateException("No se puede rechazar un indicador ya aprobado.");
        }
        indicator.setStatus("rechazado");
        indicator.setRejectedBy(userId);
        indicator.setRejectedAt(Instant.now());
        indicator.setRejectionReason(request.reason());
        return toDto(indicatorRepository.save(indicator));
    }

    private Indicator findOrThrow(UUID id) {
        return indicatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Indicador no encontrado."));
    }

    private IndicatorDto toDto(Indicator i) {
        return new IndicatorDto(
                i.getId(),
                i.getFactor().getId(),
                i.getFactor().getName(),
                i.getFactor().getCategory(),
                i.getValue(),
                i.getUnit(),
                i.getMeasuredAt(),
                i.getStatus(),
                i.getApprovedBy(),
                i.getApprovedAt(),
                i.getRejectedBy(),
                i.getRejectedAt(),
                i.getRejectionReason()
        );
    }
}
