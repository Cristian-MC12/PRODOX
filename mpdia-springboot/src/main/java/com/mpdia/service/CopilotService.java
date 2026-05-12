package com.mpdia.service;

import com.mpdia.dto.CopilotConfigDto;
import com.mpdia.entity.CopilotConfig;
import com.mpdia.repository.CopilotConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CopilotService {

    private final CopilotConfigRepository repository;

    public Optional<CopilotConfigDto> getByUser(String userId) {
        return repository.findByUserId(userId).map(this::toDto);
    }

    @Transactional
    public CopilotConfigDto save(String userId, CopilotConfigDto dto) {
        CopilotConfig config = repository.findByUserId(userId).orElse(new CopilotConfig());
        config.setUserId(userId);
        config.setTool(dto.tool());
        config.setUrl(dto.url());
        config.setApiKey(dto.apiKey());
        config.setFrequency(dto.frequency());
        config.setActive(dto.active());
        config.setUpdatedAt(Instant.now());
        return toDto(repository.save(config));
    }

    @Transactional
    public CopilotConfigDto syncNow(String userId) {
        CopilotConfig config = repository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada."));
        config.setLastSyncAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return toDto(repository.save(config));
    }

    private CopilotConfigDto toDto(CopilotConfig c) {
        return new CopilotConfigDto(
                c.getId(), c.getUserId(), c.getTool(), c.getUrl(),
                c.getApiKey(), c.getFrequency(), c.getActive(), c.getLastSyncAt()
        );
    }
}
