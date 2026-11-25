-- V2__seed_dev.sql
-- SOLO perfil dev — credencial de demostración, no usar en producción
-- Usuario: demo / Demo1234!  (BCrypt cost 12)

MERGE INTO users (id, username, password_hash, enabled, created_at)
KEY (username)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'demo',
    '$2y$12$8521c9yL8edejntbNDRBWuDiphtdoeKuG0t0FhEnwlAf0tgiarR3i',
    TRUE,
    CURRENT_TIMESTAMP
);