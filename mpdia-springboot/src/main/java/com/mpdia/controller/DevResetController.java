// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// SOLO PARA DESARROLLO — Eliminar antes de producción
package com.mpdia.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de desarrollo para limpiar datos de prueba.
 * Borra proyectos y toda su data asociada (cascada),
 * pero mantiene el catálogo de métricas y usuarios.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevResetController {

    private final JdbcTemplate jdbc;

    @DeleteMapping("/reset-proyectos")
    public ResponseEntity<Map<String, String>> resetProyectos() {
        // Borrar en orden de dependencias
        jdbc.execute("DELETE FROM registro_valores");
        jdbc.execute("DELETE FROM evaluacion_sprint");
        jdbc.execute("DELETE FROM variables");
        jdbc.execute("DELETE FROM proyecto_metricas");
        jdbc.execute("DELETE FROM metric_parametrizaciones");
        jdbc.execute("DELETE FROM metric_uso_ranking");
        jdbc.execute("DELETE FROM sprints");
        jdbc.execute("DELETE FROM project_members");
        jdbc.execute("DELETE FROM proyectos");

        return ResponseEntity.ok(Map.of("resultado", "Datos de proyectos eliminados. Catalogo de metricas intacto."));
    }

    /** GET /api/dev/diagnostico — ver estado de tablas relevantes */
    @org.springframework.web.bind.annotation.GetMapping("/diagnostico")
    public ResponseEntity<Map<String, Object>> diagnostico() {
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("proyectos", jdbc.queryForObject("SELECT COUNT(*) FROM proyectos", Integer.class));
        info.put("proyecto_metricas", jdbc.queryForObject("SELECT COUNT(*) FROM proyecto_metricas", Integer.class));
        info.put("proyecto_metricas_aprobadas", jdbc.queryForObject("SELECT COUNT(*) FROM proyecto_metricas WHERE aprobada = true", Integer.class));
        info.put("variables", jdbc.queryForObject("SELECT COUNT(*) FROM variables", Integer.class));
        info.put("variables_activas", jdbc.queryForObject("SELECT COUNT(*) FROM variables WHERE activa = true", Integer.class));
        info.put("sprints", jdbc.queryForObject("SELECT COUNT(*) FROM sprints", Integer.class));
        info.put("parametrizaciones", jdbc.queryForObject("SELECT COUNT(*) FROM metric_parametrizaciones", Integer.class));
        info.put("parametrizaciones_aprobadas", jdbc.queryForObject("SELECT COUNT(*) FROM metric_parametrizaciones WHERE status = 'aprobada'", Integer.class));
        info.put("registros", jdbc.queryForObject("SELECT COUNT(*) FROM registro_valores", Integer.class));
        var pm = jdbc.queryForList("SELECT pm.proyecto_id, pm.metrica_id, pm.aprobada, m.nombre FROM proyecto_metricas pm JOIN metricas m ON m.id = pm.metrica_id");
        info.put("detalle_proyecto_metricas", pm);
        var params = jdbc.queryForList("SELECT id, metrica_id, proyecto_id, status, user_email, objetivo FROM metric_parametrizaciones ORDER BY created_at DESC LIMIT 5");
        info.put("detalle_parametrizaciones", params);
        return ResponseEntity.ok(info);
    }
}
