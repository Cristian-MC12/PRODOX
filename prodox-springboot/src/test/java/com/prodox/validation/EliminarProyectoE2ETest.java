// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.validation;

import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import com.prodox.dto.CrearProyectoRequest;
import com.prodox.service.ProyectoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * FASE 21 — reproduce DELETE /api/proyectos/{id} contra Postgres real (mismo
 * patrón que RegistroValorUpsertTest: @Transactional revierte todo al final,
 * no persiste nada). Crea un proyecto sintético con el mismo flujo que usa el
 * frontend (ProyectoService.crear agrega el SM como miembro y genera los
 * sprints iniciales) y lo elimina, forzando un flush inmediato para que
 * cualquier excepción real de Hibernate/JPA/DB aparezca en el test en vez de
 * quedar oculta detrás de un 500 genérico en el controller.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EliminarProyectoE2ETest {

    @Autowired private ProyectoService proyectoService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private EntityManager entityManager;

    @Test
    void eliminar_proyectoConSprintsYMiembro_cascadeaSinExcepcion() {
        AppUser sm = userRepo.findByEmail("sm9130109@gmail.com").orElseThrow();

        CrearProyectoRequest req = new CrearProyectoRequest(
                "E2E-DELETE-" + UUID.randomUUID(), "test e2e de eliminación", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal e2e");

        var creado = proyectoService.crear(sm.getId().toString(), req);
        entityManager.flush();

        proyectoService.eliminar(creado.id(), sm.getId().toString());
        entityManager.flush(); // fuerza cualquier excepción real acá, no en el rollback de Spring Test
    }
}
