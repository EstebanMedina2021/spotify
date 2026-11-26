export interface SessionInfo {
  username: string;
  expiresAt: string;
}

export interface Cancion {
  id?: string;
  titulo: string;
  artista: string;
  album?: string | null;
  anno?: string | null;
  genero?: string | null;
}

export interface PlaylistSummary {
  id: string;
  nombre: string;
  descripcion: string | null;
  numeroCanciones: number;
}

export interface Playlist {
  id: string;
  nombre: string;
  descripcion: string | null;
  canciones: Cancion[];
  createdAt: string;
  updatedAt: string;
}

export interface PlaylistRequest {
  nombre: string;
  descripcion?: string | null;
  canciones: Cancion[];
}

export interface RecomendacionItem {
  titulo: string;
  artista: string;
  genero?: string | null;
  razon?: string | null;
}

export interface RecommendationResponse {
  playlistNombre: string;
  recomendaciones: RecomendacionItem[];
  fuente: string | null;
  promptVersion: string;
  fallback: boolean;
  motivoFallback: string | null;
  mensaje: string | null;
}

export interface SpotifyTrack {
  titulo: string;
  artista: string;
  album: string | null;
  spotifyId: string;
}

export interface SpotifyGenres {
  generos: string[];
  fallback: boolean;
  motivoFallback: string | null;
}

/** Normalized error built from the backend ErrorResponse (or from a network failure). */
export interface ApiError {
  status: number;
  codigo: string;
  mensaje: string;
  /** Seconds, from the Retry-After header when present. */
  retryAfter?: number;
}

export function isApiError(value: unknown): value is ApiError {
  return typeof value === 'object' && value !== null && 'codigo' in value && 'status' in value;
}
