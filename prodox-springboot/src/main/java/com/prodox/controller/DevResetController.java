// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// SOLO PARA DESARROLLO — Eliminar antes de producción
package com.prodox.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
 *
 * P0 seguridad: este controller (y sus tres endpoints, incluido
 * /api/dev/activar-variables) solo se registra como bean cuando el perfil
 * "dev" está activo explícitamente (SPRING_PROFILES_ACTIVE=dev o
 * spring.profiles.active=dev). Fail-closed por diseño: ningún perfil
 * activa "dev" por defecto — ni la ausencia de spring.profiles.active en
 * el application.properties local, ni SPRING_PROFILES_ACTIVE=prod en
 * Railway (ver RAILWAY_DEPLOYMENT.md) lo activan. Sin este perfil, Spring
 * no registra ninguna ruta bajo /api/dev/** — no es una cuestión de
 * permisos o autenticación, el controller simplemente no existe en el
 * contexto de la aplicación.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Profile("dev")
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

    /** POST /api/dev/activar-variables — Activar todas las variables */
    @org.springframework.web.bind.annotation.PostMapping("/activar-variables")
    public ResponseEntity<Map<String, Object>> activarVariables() {
        int updated = jdbc.update("UPDATE variables SET activa = true WHERE activa = false");
        return ResponseEntity.ok(Map.of("variables_activadas", updated));
    }
}
