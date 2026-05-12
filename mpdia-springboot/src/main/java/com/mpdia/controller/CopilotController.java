package com.mpdia.controller;

import com.mpdia.dto.CopilotConfigDto;
import com.mpdia.service.CopilotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;

    /** GET /api/copilot/config */
    @GetMapping("/config")
    public ResponseEntity<CopilotConfigDto> get(Authentication auth) {
        return copilotService.getByUser(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** PUT /api/copilot/config */
    @PutMapping("/config")
    public ResponseEntity<CopilotConfigDto> save(@Valid @RequestBody CopilotConfigDto dto,
                                                  Authentication auth) {
        return ResponseEntity.ok(copilotService.save(auth.getName(), dto));
    }

    /** POST /api/copilot/sync */
    @PostMapping("/sync")
    public ResponseEntity<CopilotConfigDto> sync(Authentication auth) {
        return ResponseEntity.ok(copilotService.syncNow(auth.getName()));
    }
}
