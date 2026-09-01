// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
// app_users es propiedad del rol "postgres" y mpdia_user (el usuario de la
// app) no tiene privilegio ALTER sobre ella (ver V33__app_users_nombre.sql),
// así que "nombre" vive en una tabla nueva propia de mpdia_user, unida 1:1
// por PK — transparente para el resto del código: user.getNombre() /
// setNombre() funcionan igual que si fuera una columna de app_users.
@SecondaryTable(name = "app_user_profiles", pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"))
@Data
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "scrum_member";

    /** Nombre real del usuario (mostrado en el sidebar). Nullable: usuarios
     *  existentes antes de V33 no lo tienen. */
    @Column(table = "app_user_profiles")
    private String nombre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
