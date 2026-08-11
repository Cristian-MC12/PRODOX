// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje del historial de conversación con el AI Copilot.
 * Cada mensaje pertenece a un usuario y proyecto específico.
 */
@Entity
@Table(name = "ai_chat_messages")
@Getter @Setter @NoArgsConstructor
public class AIChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ID del usuario (String UUID) que envió/recibió el mensaje */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Proyecto en el que se generó la conversación */
    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    /** Sprint específico del contexto (opcional) */
    @Column(name = "sprint_id")
    private UUID sprintId;

    /** Rol del mensaje: 'user' | 'assistant' | 'system' */
    @Column(nullable = false, length = 20)
    private String role;

    /** Contenido del mensaje */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Timestamp de creación del mensaje */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
