package com.prodox.validation;

import com.prodox.entity.*;
import com.prodox.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * FASE 16.8.1 - VALIDACIÓN DE DATOS REALES
 * Consulta datos existentes en PostgreSQL sin modificarlos
 */
@SpringBootTest
@ActiveProfiles("test")
public class Fase1681ValidationTest {

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private MetricaRepository metricaRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private MetricParametrizacionRepository parametrizacionRepository;

    @Autowired
    private VariableRepository variableRepository;

    @Autowired
    private RegistroValorRepository registroValorRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void resumenCompleto() {
        System.out.println("\n");
        System.out.println("=".repeat(60));
        System.out.println("RESUMEN DE DATOS REALES - FASE 16.8.1");
        System.out.println("=".repeat(60));
        
        long totalProyectos = proyectoRepository.count();
        long totalMetricas = metricaRepository.count();
        long totalSprints = sprintRepository.count();
        long totalParametrizaciones = parametrizacionRepository.count();
        long totalVariables = variableRepository.count();
        long totalValores = registroValorRepository.count();
        long totalUsuarios = appUserRepository.count();
        
        System.out.println("Proyectos:                    " + totalProyectos);
        System.out.println("Métricas:                     " + totalMetricas);
        System.out.println("Sprints:                      " + totalSprints);
        System.out.println("Parametrizaciones totales:    " + totalParametrizaciones);
        System.out.println("Variables dinámicas:          " + totalVariables);
        System.out.println("Valores registrados:          " + totalValores);
        System.out.println("Usuarios:                     " + totalUsuarios);
        System.out.println("=".repeat(60));
        
        // Mostrar detalles básicos
        System.out.println("\n========== PROYECTOS ==========");
        proyectoRepository.findAll().forEach(p -> 
            System.out.println(String.format("- [%s] %s", p.getId(), p.getNombre()))
        );
        
        System.out.println("\n========== MÉTRICAS ==========");
        metricaRepository.findAll().forEach(m -> 
            System.out.println(String.format("- [%s] %s", m.getId(), m.getNombre()))
        );
        
        System.out.println("\n========== PARAMETRIZACIONES ==========");
        parametrizacionRepository.findAll().forEach(p -> 
            System.out.println(String.format("- [%s] v%d - %s - %s", 
                p.getId(), p.getVersion(), p.getStatus(),
                p.getConfiguracionAprobadaJson() != null ? "CON JSON" : "SIN JSON"))
        );
    }
}
