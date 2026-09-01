// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.MetricaDto;
import com.prodox.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricaService {

    private final MetricaRepository metricaRepo;

    public List<MetricaDto> listar() {
        return metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc().stream()
                .map(m -> new MetricaDto(
                        m.getId(), m.getCodigo(), m.getNombre(),
                        m.getDescripcion(), m.getCategoria().getNombre()))
                .toList();
    }

    public List<MetricaDto> listarPorCategoria(String categoria) {
        return metricaRepo.findByCategoriaNombre(categoria).stream()
                .map(m -> new MetricaDto(
                        m.getId(), m.getCodigo(), m.getNombre(),
                        m.getDescripcion(), m.getCategoria().getNombre()))
                .toList();
    }
}
