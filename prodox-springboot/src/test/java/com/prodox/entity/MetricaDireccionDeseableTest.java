package com.prodox.entity;

import com.prodox.repository.MetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para validación de dirección deseable de métricas.
 * Verifica las reglas de coherencia entre direccionDeseable y valorObjetivo.
 * 
 * Usa el enfoque de persistencia para activar los hooks @PrePersist/@PreUpdate.
 */
@SpringBootTest
@Transactional
class MetricaDireccionDeseableTest {

    @Autowired
    private MetricaRepository metricaRepository;

    private MetricaCategoria categoriaTest;

    @BeforeEach
    void setUp() {
        // Usar una categoría existente (ID=1 suele existir en datos iniciales)
        categoriaTest = new MetricaCategoria();
        categoriaTest.setId((short) 1);
        categoriaTest.setNombre("Categoría Test");
    }

    @Test
    void testDireccionDeseableNull_Valida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-001");
        metrica.setNombre("Métrica Test Null");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable(null);
        metrica.setValorObjetivo(null);

        // No debe lanzar excepción
        assertDoesNotThrow(() -> metricaRepository.save(metrica));
    }

    @Test
    void testDireccionMayorMejor_SinObjetivo_Valida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-002");
        metrica.setNombre("Métrica Mayor Mejor");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("MAYOR_MEJOR");
        metrica.setValorObjetivo(null);

        assertDoesNotThrow(() -> metricaRepository.save(metrica));
    }

    @Test
    void testDireccionMenorMejor_SinObjetivo_Valida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-003");
        metrica.setNombre("Métrica Menor Mejor");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("MENOR_MEJOR");
        metrica.setValorObjetivo(null);

        assertDoesNotThrow(() -> metricaRepository.save(metrica));
    }

    @Test
    void testDireccionObjetivo_ConValorObjetivo_Valida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-004");
        metrica.setNombre("Métrica Objetivo");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("OBJETIVO");
        metrica.setValorObjetivo(new BigDecimal("100.00"));

        assertDoesNotThrow(() -> metricaRepository.save(metrica));
    }

    @Test
    void testDireccionObjetivo_SinValorObjetivo_Invalida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-005");
        metrica.setNombre("Métrica Objetivo Sin Valor");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("OBJETIVO");
        metrica.setValorObjetivo(null);

        Exception ex = assertThrows(
            Exception.class,
            () -> metricaRepository.save(metrica)
        );
        
        // Spring Data JPA envuelve la excepción de @PrePersist en InvalidDataAccessApiUsageException
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof IllegalStateException, 
            "Se esperaba IllegalStateException pero se obtuvo: " + cause.getClass().getName());
        assertTrue(cause.getMessage().contains("OBJETIVO") && cause.getMessage().contains("valor_objetivo"),
            "Mensaje debe indicar que OBJETIVO requiere valor_objetivo. Mensaje real: " + cause.getMessage());
    }

    @Test
    void testDireccionMayorMejor_ConObjetivo_Invalida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-006");
        metrica.setNombre("Métrica Mayor Mejor Con Objetivo");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("MAYOR_MEJOR");
        metrica.setValorObjetivo(new BigDecimal("50.00"));

        Exception ex = assertThrows(
            Exception.class,
            () -> metricaRepository.save(metrica)
        );
        
        // Spring Data JPA envuelve la excepción de @PrePersist en InvalidDataAccessApiUsageException
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof IllegalStateException,
            "Se esperaba IllegalStateException pero se obtuvo: " + cause.getClass().getName());
        assertTrue(cause.getMessage().contains("valor_objetivo") && 
                   (cause.getMessage().contains("NULL") || cause.getMessage().contains("solo")),
            "Mensaje debe indicar que valor_objetivo debe ser NULL. Mensaje real: " + cause.getMessage());
    }

    @Test
    void testDireccionMenorMejor_ConObjetivo_Invalida() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-007");
        metrica.setNombre("Métrica Menor Mejor Con Objetivo");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("MENOR_MEJOR");
        metrica.setValorObjetivo(new BigDecimal("25.00"));

        Exception ex = assertThrows(
            Exception.class,
            () -> metricaRepository.save(metrica)
        );
        
        // Spring Data JPA envuelve la excepción de @PrePersist en InvalidDataAccessApiUsageException
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof IllegalStateException,
            "Se esperaba IllegalStateException pero se obtuvo: " + cause.getClass().getName());
        assertTrue(cause.getMessage().contains("valor_objetivo") && 
                   (cause.getMessage().contains("NULL") || cause.getMessage().contains("solo")),
            "Mensaje debe indicar que valor_objetivo debe ser NULL. Mensaje real: " + cause.getMessage());
    }

    @Test
    void testDireccionInvalida_LanzaExcepcion() {
        Metrica metrica = new Metrica();
        metrica.setCodigo("TEST-DIR-008");
        metrica.setNombre("Métrica Dirección Inválida");
        metrica.setDescripcion("Descripción");
        metrica.setCategoria(categoriaTest);
        metrica.setDireccionDeseable("DIRECCION_INVALIDA");
        metrica.setValorObjetivo(null);

        Exception ex = assertThrows(
            Exception.class,
            () -> metricaRepository.save(metrica)
        );
        
        // Spring Data JPA envuelve la excepción de @PrePersist en InvalidDataAccessApiUsageException
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof IllegalStateException,
            "Se esperaba IllegalStateException pero se obtuvo: " + cause.getClass().getName());
        assertTrue(cause.getMessage().contains("inválida") || cause.getMessage().contains("invalid"),
            "Mensaje debe indicar dirección inválida. Mensaje real: " + cause.getMessage());
    }

    @Test
    void testGetDireccionDeseableEnum_Null() {
        Metrica metrica = new Metrica();
        metrica.setDireccionDeseable(null);

        assertNull(metrica.getDireccionDeseableEnum());
    }

    @Test
    void testGetDireccionDeseableEnum_MayorMejor() {
        Metrica metrica = new Metrica();
        metrica.setDireccionDeseable("MAYOR_MEJOR");

        assertEquals(DireccionDeseableMetrica.MAYOR_MEJOR, metrica.getDireccionDeseableEnum());
    }

    @Test
    void testSetDireccionDeseableEnum_Null() {
        Metrica metrica = new Metrica();
        metrica.setDireccionDeseableEnum(null);

        assertNull(metrica.getDireccionDeseable());
    }

    @Test
    void testSetDireccionDeseableEnum_Objetivo() {
        Metrica metrica = new Metrica();
        metrica.setDireccionDeseableEnum(DireccionDeseableMetrica.OBJETIVO);

        assertEquals("OBJETIVO", metrica.getDireccionDeseable());
    }
}
