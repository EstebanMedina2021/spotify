import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import { API_BASE } from './api';
import { Playlist, PlaylistRequest, PlaylistSummary, RecommendationResponse } from './models';

const RECOMMENDATION_TTL_MS = 10 * 60 * 1000; // same as the backend cache

@Injectable({ providedIn: 'root' })
export class PlaylistService {
  private readonly http = inject(HttpClient);
  private readonly recommendationCache = new Map<string, { at: number; value: RecommendationResponse }>();

  readonly playlists = signal<PlaylistSummary[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);

  refresh(): void {
    this.loading.set(true);
    this.http.get<PlaylistSummary[]>(`${API_BASE}/lists`).subscribe({
      next: (list) => {
        this.playlists.set(list);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.loaded.set(true);
        this.loading.set(false);
      },
    });
  }

  get(nombre: string): Observable<Playlist> {
    return this.http.get<Playlist>(this.url(nombre));
  }

  create(request: PlaylistRequest): Observable<Playlist> {
    return this.http.post<Playlist>(`${API_BASE}/lists`, request).pipe(tap(() => this.refresh()));
  }

  remove(nombre: string): Observable<void> {
    return this.http.delete<void>(this.url(nombre)).pipe(
      tap(() => {
        this.recommendationCache.delete(this.cacheKey(nombre));
        this.refresh();
      }),
    );
  }

  /** Recommendations are cached for 10 min, except fallbacks (mirrors the backend rule). */
  recommendations(nombre: string): Observable<RecommendationResponse> {
    const key = this.cacheKey(nombre);
    const cached = this.recommendationCache.get(key);
    if (cached && Date.now() - cached.at < RECOMMENDATION_TTL_MS) {
      return of(cached.value);
    }
    return this.http.get<RecommendationResponse>(`${this.url(nombre)}/recommendations`).pipe(
      tap((value) => {
        if (!value.fallback && value.recomendaciones.length > 0) {
          this.recommendationCache.set(key, { at: Date.now(), value });
        }
      }),
    );
  }

  private url(nombre: string): string {
    return `${API_BASE}/lists/${encodeURIComponent(nombre)}`;
  }

  private cacheKey(nombre: string): string {
    return nombre.trim().toLowerCase();
  }
}
