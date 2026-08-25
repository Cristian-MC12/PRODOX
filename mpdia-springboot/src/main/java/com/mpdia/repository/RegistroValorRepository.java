// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.RegistroValor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroValorRepository extends JpaRepository<RegistroValor, UUID> {
    List<RegistroValor> findBySprintId(UUID sprintId);
    List<RegistroValor> findByVariable_IdAndSprintId(UUID variableId, UUID sprintId);
    List<RegistroValor> findBySprintIdAndUserId(UUID sprintId, String userId);
    List<RegistroValor> findByVariable_IdOrderByRegistradoAtAsc(UUID variableId);

    // Fase 16.7: Búsqueda por sprint y variable
    List<RegistroValor> findBySprintIdAndVariable_Id(UUID sprintId, UUID variableId);

    // Fase 16.11: localizar el registro vigente (más reciente) para upsert,
    // sin tocar los duplicados históricos que ya pudieran existir para esa
    // misma combinación — solo el más reciente se reutiliza hacia adelante.
    Optional<RegistroValor> findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(
        UUID sprintId, UUID variableId);
    Optional<RegistroValor> findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
        UUID sprintId, UUID variableId, String userId);

    // FASE 16 — captura por fecha explícita: localizar el registro EXACTO de esa
    // fecha (no "el más reciente") para poder decidir entre actualizar (misma
    // fecha) o crear una fila nueva (fecha distinta), permitiendo que coexistan
    // varias capturas de la misma variable+sprint en fechas diferentes.
    Optional<RegistroValor> findFirstBySprintIdAndVariable_IdAndRegistradoAt(
        UUID sprintId, UUID variableId, java.time.Instant registradoAt);
    Optional<RegistroValor> findFirstBySprintIdAndVariable_IdAndUserIdAndRegistradoAt(
        UUID sprintId, UUID variableId, String userId, java.time.Instant registradoAt);
}

