import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { API_BASE } from './api';
import { SpotifyGenres, SpotifyTrack } from './models';

@Injectable({ providedIn: 'root' })
export class SpotifyService {
  private readonly http = inject(HttpClient);
  private genres$?: Observable<SpotifyGenres>;

  search(query: string, limit = 10): Observable<SpotifyTrack[]> {
    const params = new HttpParams().set('q', query).set('limit', limit);
    return this.http
      .get<{ resultados: SpotifyTrack[] }>(`${API_BASE}/spotify/search`, { params })
      .pipe(map((response) => response.resultados));
  }

  /** The backend caches genres for 1 h; one request per page load is enough here. */
  genres(): Observable<SpotifyGenres> {
    this.genres$ ??= this.http
      .get<SpotifyGenres>(`${API_BASE}/spotify/genres`)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.genres$;
  }
}
