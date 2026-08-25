// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * FASE 15 — "Crear métrica con IA": Paso 3, versión de nombre/descripción ya
 * revisada/editada y confirmada explícitamente por el Scrum Master
 * ("Usar esta propuesta"). Es esta versión — nunca la respuesta original de
 * Gemini — la que se persiste como Metrica.
 *
 * Los demás campos de la propuesta (objetivo, variables sugeridas, tipo de
 * operación, fórmula, unidad, fuente) no se envían aquí: no forman parte de
 * la entidad Metrica, sino de la parametrización — el frontend los usa para
 * pre-llenar el formulario de "Parametrizar con GenAI" ya existente, sin que
 * el backend necesite persistirlos por separado (ver ParametrizacionService).
 *
 * proyectoId identifica el proyecto activo, pero NUNCA es la única garantía
 * de acceso: MetricaIAService valida además que el usuario autenticado
 * pertenezca a ese proyecto (mismo patrón que ParametrizacionService).
 *
 * FASE 23 — objetivo/queMide/variablesSugeridas son OPCIONALES y nunca se
 * persisten en Metrica: solo se usan como señal adicional para
 * MetricaSimilitudService (detección de posibles duplicados conceptuales),
 * igual que ya se usaban en el frontend para pre-llenar la parametrización.
 * confirmarCreacionDiferente, cuando es true, indica que el Scrum Master ya
 * vio los posibles duplicados conceptuales y decidió explícitamente crear la
 * métrica de todas formas — en ese caso se omite la búsqueda de posibles
 * duplicados (el chequeo de nombre EXACTO sigue aplicando siempre, sin
 * excepción, porque nunca puede omitirse).
 */
public record CrearMetricaIARequest(
    @NotNull UUID proyectoId,
    @NotNull Short categoriaId,
    @NotBlank String nombre,
    @NotBlank String descripcion,
    String objetivo,
    String queMide,
    String variablesSugeridas,
    Boolean confirmarCreacionDiferente
) {}
