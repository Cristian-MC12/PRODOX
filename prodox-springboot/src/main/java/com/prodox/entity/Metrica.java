// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "metricas")
@Getter @Setter @NoArgsConstructor
public class Metrica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private MetricaCategoria categoria;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(length = 120)
    private String factor;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Dirección deseable de la métrica para interpretación de tendencias.
     * MAYOR_MEJOR, MENOR_MEJOR, OBJETIVO o NULL (no configurada).
     * 
     * V50 — Fase: Calidad analítica de módulos de IA (Prioridad 1)
     */
    @Column(name = "direccion_deseable", length = 20)
    private String direccionDeseable;

    /**
     * Valor objetivo numérico cuando direccionDeseable='OBJETIVO'.
     * Debe ser NULL para otras direcciones.
     * 
     * V50 — Fase: Calidad analítica de módulos de IA (Prioridad 1)
     */
    @Column(name = "valor_objetivo", precision = 15, scale = 4)
    private BigDecimal valorObjetivo;

    /*
     * Metrica es EXCLUSIVAMENTE el catálogo global de definiciones de
     * métrica — nunca tiene un proyecto propietario. La asociación entre un
     * proyecto y una Metrica vive en ProyectoMetrica, nunca acá (revisión
     * post-implementación de la Fase PRODOX AI: V30 había agregado una
     * columna proyecto_id que contradecía este modelo — revertida en V31).
     */

    /**
     * Validación de coherencia entre direccionDeseable y valorObjetivo.
     * Ejecutada antes de persistir o actualizar la entidad.
     */
    @PrePersist
    @PreUpdate
    private void validarCoherenciaDireccionObjetivo() {
        if (direccionDeseable != null) {
            DireccionDeseableMetrica direccion;
            try {
                direccion = DireccionDeseableMetrica.fromString(direccionDeseable);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                    "Dirección deseable inválida: '" + direccionDeseable + "'. " +
                    "Valores permitidos: MAYOR_MEJOR, MENOR_MEJOR, OBJETIVO o NULL.");
            }

            if (direccion.requiereValorObjetivo() && valorObjetivo == null) {
                throw new IllegalStateException(
                    "La métrica '" + nombre + "' tiene direccion_deseable=OBJETIVO " +
                    "pero valor_objetivo es NULL. Debe especificar un valor objetivo válido.");
            }

            if (direccion.valorObjetivoDebeSerNull() && valorObjetivo != null) {
                throw new IllegalStateException(
                    "La métrica '" + nombre + "' tiene direccion_deseable=" + direccion + 
                    " pero valor_objetivo no es NULL. El valor objetivo solo es válido " +
                    "para direccion_deseable=OBJETIVO.");
            }
        } else {
            // Si direccionDeseable es NULL, valorObjetivo también debe serlo
            if (valorObjetivo != null) {
                throw new IllegalStateException(
                    "La métrica '" + nombre + "' tiene valor_objetivo definido " +
                    "pero direccion_deseable es NULL. Debe definir la dirección deseable " +
                    "o dejar ambos campos en NULL.");
            }
        }
    }

    /**
     * Obtiene la dirección deseable como enum (null-safe).
     * 
     * @return DireccionDeseableMetrica o null si no está configurada
     */
    @Transient
    public DireccionDeseableMetrica getDireccionDeseableEnum() {
        return DireccionDeseableMetrica.fromString(direccionDeseable);
    }

    /**
     * Establece la dirección deseable desde el enum.
     * 
     * @param direccion DireccionDeseableMetrica o null
     */
    public void setDireccionDeseableEnum(DireccionDeseableMetrica direccion) {
        this.direccionDeseable = direccion != null ? direccion.name() : null;
    }
}
