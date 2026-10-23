package com.prodox.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

/**
 * Auditoría de esquema de base de datos - Solo lectura.
 * NO usa Mockito, NO modifica datos.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
class DatabaseSchemaAudit {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void auditarHistorialFlyway() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUDITORÍA: HISTORIAL DE MIGRACIONES FLYWAY");
        System.out.println("=".repeat(80) + "\n");

        String sql = """
            SELECT installed_rank, version, description, installed_on, success
            FROM flyway_schema_history
            ORDER BY installed_rank DESC
            LIMIT 10
            """;

        List<Map<String, Object>> historial = jdbcTemplate.queryForList(sql);
        
        System.out.println("Últimas 10 migraciones aplicadas:");
        System.out.println("-".repeat(80));
        for (Map<String, Object> row : historial) {
            System.out.printf("V%-3s | %-45s | %s | %s%n",
                row.get("version"),
                row.get("description"),
                row.get("installed_on"),
                row.get("success"));
        }
        System.out.println();
    }

    @Test
    void auditarColumnasMetricas() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUDITORÍA: COLUMNAS DE TABLA METRICAS");
        System.out.println("=".repeat(80) + "\n");

        String sql = """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'public' 
              AND table_name = 'metricas'
              AND column_name IN ('direccion_deseable', 'valor_objetivo')
            ORDER BY column_name
            """;

        List<Map<String, Object>> columnas = jdbcTemplate.queryForList(sql);
        
        if (columnas.isEmpty()) {
            System.out.println("✗ Las columnas 'direccion_deseable' y 'valor_objetivo' NO EXISTEN");
            System.out.println("  Migración V44 NO ha sido aplicada");
        } else {
            System.out.println("✓ Columnas encontradas:");
            System.out.println("-".repeat(80));
            for (Map<String, Object> col : columnas) {
                System.out.printf("  %-25s | %-15s | Nullable: %-3s | Default: %s%n",
                    col.get("column_name"),
                    col.get("data_type"),
                    col.get("is_nullable"),
                    col.get("column_default"));
            }
        }
        System.out.println();
    }

    @Test
    void auditarTodasColumnasMetricas() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUDITORÍA: TODAS LAS COLUMNAS DE TABLA METRICAS");
        System.out.println("=".repeat(80) + "\n");

        String sql = """
            SELECT column_name, data_type, is_nullable, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'metricas'
            ORDER BY ordinal_position
            """;

        List<Map<String, Object>> columnas = jdbcTemplate.queryForList(sql);
        
        System.out.println("Total de columnas: " + columnas.size());
        System.out.println("-".repeat(80));
        for (Map<String, Object> col : columnas) {
            System.out.printf("%2d. %-30s | %-20s | Nullable: %s%n",
                col.get("ordinal_position"),
                col.get("column_name"),
                col.get("data_type"),
                col.get("is_nullable"));
        }
        System.out.println();
    }

    @Test
    void auditarMetricasExistentes() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AUDITORÍA: MÉTRICAS EXISTENTES EN BASE DE DATOS");
        System.out.println("=".repeat(80) + "\n");

        Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM metricas", 
            Integer.class);
        
        System.out.println("Total de métricas en catálogo: " + total);
        
        String sqlSample = """
            SELECT codigo, nombre, categoria_id
            FROM metricas
            ORDER BY categoria_id, nombre
            LIMIT 10
            """;
        
        List<Map<String, Object>> muestra = jdbcTemplate.queryForList(sqlSample);
        
        System.out.println("\nMuestra de primeras 10 métricas:");
        System.out.println("-".repeat(80));
        for (Map<String, Object> m : muestra) {
            System.out.printf("%-15s | Cat: %2d | %s%n",
                m.get("codigo"),
                m.get("categoria_id"),
                m.get("nombre"));
        }
        System.out.println();
    }

    @Test
    void generarResumenAuditoria() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESUMEN DE AUDITORÍA - MIGRACIÓN V44");
        System.out.println("=".repeat(80) + "\n");

        // Última versión
        String sqlVersion = """
            SELECT version, description 
            FROM flyway_schema_history 
            WHERE success = true 
            ORDER BY installed_rank DESC 
            LIMIT 1
            """;
        Map<String, Object> ultima = jdbcTemplate.queryForMap(sqlVersion);
        
        // Columnas V44
        String sqlColumnas = """
            SELECT COUNT(*) 
            FROM information_schema.columns
            WHERE table_schema = 'public' 
              AND table_name = 'metricas'
              AND column_name IN ('direccion_deseable', 'valor_objetivo')
            """;
        Integer columnasV44 = jdbcTemplate.queryForObject(sqlColumnas, Integer.class);
        
        // Total métricas
        Integer totalMetricas = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM metricas", Integer.class);

        System.out.println("1. ESQUEMA ACTUAL:");
        System.out.printf("   Última migración aplicada: V%s (%s)%n", 
            ultima.get("version"), ultima.get("description"));
        System.out.println();
        
        System.out.println("2. ESTADO DE V44:");
        if (columnasV44 == 2) {
            System.out.println("   ✓ Columnas de V44 EXISTEN en la base de datos");
            System.out.println("   ✓ La migración fue aplicada previamente");
        } else if (columnasV44 == 0) {
            System.out.println("   ✗ Columnas de V44 NO EXISTEN en la base de datos");
            System.out.println("   ✗ La migración V44 NO ha sido aplicada");
        } else {
            System.out.println("   ⚠ Estado inconsistente: solo " + columnasV44 + " de 2 columnas existen");
        }
        System.out.println();
        
        System.out.println("3. DATOS EXISTENTES:");
        System.out.println("   Total de métricas en catálogo: " + totalMetricas);
        System.out.println("   Todas quedarían con direccionDeseable=NULL tras aplicar V44");
        System.out.println();
        
        System.out.println("4. RECOMENDACIÓN:");
        if (columnasV44 == 0) {
            String versionActual = (String) ultima.get("version");
            int numeroVersion = Integer.parseInt(versionActual);
            int proximaVersion = numeroVersion + 1;
            
            System.out.println("   • Renumerar V44 como V" + proximaVersion + "__metrica_direccion_deseable.sql");
            System.out.println("   • Aplicar con: mvn flyway:migrate");
            System.out.println("   • Verificar con esta misma auditoría");
        } else if (columnasV44 == 2) {
            System.out.println("   • Las columnas ya existen, no aplicar V44 nuevamente");
            System.out.println("   • Verificar si V44 está en historial de Flyway");
        }
        
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
