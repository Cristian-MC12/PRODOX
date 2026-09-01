package com.prodox.validation;

import com.prodox.entity.*;
import com.prodox.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * FASE 16.8.1 - Obtener IDs reales para validación
 */
@SpringBootTest
@ActiveProfiles("test")
public class ObtenerIdsRealesTest {

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private MetricaRepository metricaRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Test
    void obtenerIdProyectoTrabajo1() {
        System.out.println("\n========== PROYECTO TRABAJO 1 ==========");
        List<Proyecto> proyectos = proyectoRepository.findAll();
        proyectos.stream()
            .filter(p -> p.getNombre().contains("Trabajo"))
            .forEach(p -> {
                System.out.println("ID: " + p.getId());
                System.out.println("Nombre: " + p.getNombre());
                System.out.println("Descripción: " + p.getDescripcion());
            });
    }

    @Test
    void obtenerIdMetricaProblemasReportados() {
        System.out.println("\n========== MÉTRICA PROBLEMAS REPORTADOS ==========");
        List<Metrica> metricas = metricaRepository.findAll();
        metricas.stream()
            .filter(m -> m.getNombre().toLowerCase().contains("problemas") && 
                        m.getNombre().toLowerCase().contains("cliente"))
            .forEach(m -> {
                System.out.println("ID: " + m.getId());
                System.out.println("Nombre: " + m.getNombre());
                System.out.println("Código: " + m.getCodigo());
                System.out.println("Descripción: " + m.getDescripcion());
                System.out.println("Categoría: " + m.getCategoria().getNombre());
            });
    }

    @Test
    void obtenerSprintsDelProyecto() {
        System.out.println("\n========== SPRINTS DEL PROYECTO TRABAJO 1 ==========");
        List<Proyecto> proyectos = proyectoRepository.findAll();
        proyectos.stream()
            .filter(p -> p.getNombre().contains("Trabajo"))
            .findFirst()
            .ifPresent(proyecto -> {
                List<Sprint> sprints = sprintRepository.findByProyectoIdOrderByNumeroDesc(proyecto.getId());
                System.out.println("Total sprints: " + sprints.size());
                sprints.forEach(s -> {
                    System.out.println("\nSprint ID: " + s.getId());
                    System.out.println("Número: " + s.getNumero());
                    System.out.println("Fecha inicio: " + s.getFechaInicio());
                    System.out.println("Fecha fin: " + s.getFechaFin());
                    System.out.println("Estado: " + s.getEstado());
                });
            });
    }

    @Test
    void resumenDatosParaValidacion() {
        System.out.println("\n");
        System.out.println("=".repeat(70));
        System.out.println("DATOS NECESARIOS PARA VALIDACIÓN END-TO-END");
        System.out.println("=".repeat(70));
        
        // Proyecto
        List<Proyecto> proyectos = proyectoRepository.findAll();
        Proyecto proyecto = proyectos.stream()
            .filter(p -> p.getNombre().contains("Trabajo"))
            .findFirst()
            .orElse(null);
            
        if (proyecto != null) {
            System.out.println("\n1. PROYECTO:");
            System.out.println("   ID: " + proyecto.getId());
            System.out.println("   Nombre: " + proyecto.getNombre());
            
            // Sprints del proyecto
            List<Sprint> sprints = sprintRepository.findByProyectoIdOrderByNumeroDesc(proyecto.getId());
            System.out.println("\n2. SPRINTS DISPONIBLES: " + sprints.size());
            if (!sprints.isEmpty()) {
                Sprint sprint1 = sprints.get(sprints.size() - 1); // El primero por número
                System.out.println("   Sprint 1:");
                System.out.println("   ID: " + sprint1.getId());
                System.out.println("   Número: " + sprint1.getNumero());
                System.out.println("   Estado: " + sprint1.getEstado());
            }
        }
        
        // Métrica
        List<Metrica> metricas = metricaRepository.findAll();
        Metrica metricaProblemas = metricas.stream()
            .filter(m -> m.getNombre().toLowerCase().contains("problemas") && 
                        m.getNombre().toLowerCase().contains("cliente"))
            .findFirst()
            .orElse(null);
            
        if (metricaProblemas != null) {
            System.out.println("\n3. MÉTRICA:");
            System.out.println("   ID: " + metricaProblemas.getId());
            System.out.println("   Nombre: " + metricaProblemas.getNombre());
            System.out.println("   Código: " + metricaProblemas.getCodigo());
        }
        
        System.out.println("\n" + "=".repeat(70));
    }
}
