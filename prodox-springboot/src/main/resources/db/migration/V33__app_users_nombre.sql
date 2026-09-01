-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V33 — Nombre real del usuario (necesario para mostrarlo en el sidebar en
-- vez de un nombre fijo).
--
-- NOTA: app_users es propiedad del rol "postgres" (creada por V1__init.sql
-- fuera de Flyway-como-mpdia_user), y el usuario de la aplicación
-- (mpdia_user) no tiene privilegio ALTER sobre ella — un ALTER TABLE
-- app_users aquí falla con "debe ser dueño de la tabla app_users" y tumba
-- el arranque completo de la app. Se usa una tabla nueva en su lugar
-- (que mpdia_user sí puede crear y de la que sí es dueño, como ya ocurre
-- con project_invitaciones en V8), mapeada como @SecondaryTable de AppUser.
CREATE TABLE IF NOT EXISTS app_user_profiles (
    user_id UUID PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    nombre  VARCHAR(255)
);
