-- V1__schema.sql
-- Schema for Playlist API (H2 compatible with PostgreSQL mode)

CREATE TABLE IF NOT EXISTS users (
    id              UUID            PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    password_hash   VARCHAR(100)    NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS playlists (
    id                  UUID            PRIMARY KEY,
    nombre              VARCHAR(50)     NOT NULL,
    nombre_normalizado  VARCHAR(50)     NOT NULL,
    descripcion         VARCHAR(200),
    owner_id            UUID            NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_playlist_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT uk_playlist_owner_nombre UNIQUE (owner_id, nombre_normalizado)
);

CREATE TABLE IF NOT EXISTS canciones (
    id              UUID            PRIMARY KEY,
    playlist_id     UUID            NOT NULL,
    posicion        INT             NOT NULL,
    titulo          VARCHAR(100)    NOT NULL,
    artista         VARCHAR(100)    NOT NULL,
    album           VARCHAR(100),
    anno            VARCHAR(10),
    genero          VARCHAR(50),
    CONSTRAINT fk_cancion_playlist FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cancion_playlist ON canciones(playlist_id);
CREATE INDEX IF NOT EXISTS idx_playlist_owner ON playlists(owner_id);