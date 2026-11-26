import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap, tap } from 'rxjs';
import { Cancion, isApiError, Playlist, RecommendationResponse } from '../core/models';
import { PlayerService } from '../core/player.service';
import { PlaylistService } from '../core/playlist.service';
import { ToastService } from '../core/toast.service';
import { headerColor } from '../core/util';
import { TrackCard } from '../shared/cards';
import { Cover } from '../shared/cover';
import { Equalizer } from '../shared/equalizer';
import { FallbackBanner } from '../shared/fallback-banner';
import { Icon } from '../shared/icon';
import { Skeleton } from '../shared/skeleton';

type Status = 'loading' | 'ready' | 'notfound' | 'error';

@Component({
  selector: 'app-playlist-page',
  imports: [RouterLink, Cover, Icon, Equalizer, Skeleton, FallbackBanner, TrackCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (status()) {
      @case ('loading') {
        <div class="hero" style="background: #2a2a2a">
          <app-skeleton width="200px" height="200px" />
          <div class="hero-text">
            <app-skeleton width="80px" height="12px" />
            <app-skeleton width="320px" height="48px" />
            <app-skeleton width="200px" height="14px" />
          </div>
        </div>
        <div class="body">
          @for (i of [1, 2, 3, 4, 5]; track i) {
            <app-skeleton height="40px" />
          }
        </div>
      }
      @case ('notfound') {
        <div class="state">
          <h2>Esta playlist no existe</h2>
          <p>Puede que se haya eliminado o que no te pertenezca.</p>
          <a class="btn primary" routerLink="/">Ir al inicio</a>
        </div>
      }
      @case ('error') {
        <div class="state">
          <h2>No se pudo cargar la playlist</h2>
          <p>{{ errorMessage() }}</p>
        </div>
      }
      @case ('ready') {
        @if (playlist(); as pl) {
          <div class="hero" [style.background]="'linear-gradient(180deg, ' + color() + ' 0%, #121212 100%)'">
            <div class="cover-box"><app-cover [seed]="pl.nombre" [size]="200" /></div>
            <div class="hero-text">
              <small>PLAYLIST</small>
              <h1>{{ pl.nombre }}</h1>
              @if (pl.descripcion) {
                <p class="desc">{{ pl.descripcion }}</p>
              }
              <p class="meta">
                {{ pl.canciones.length }} {{ pl.canciones.length === 1 ? 'canción' : 'canciones' }}
              </p>
            </div>
          </div>

          <div class="body">
            <div class="actions">
              <span class="wrap" title="Reproducción no disponible: esta versión no incluye una fuente de audio.">
                <button type="button" class="play" disabled aria-label="Reproducir">
                  <app-icon name="play" [size]="28" />
                </button>
              </span>
              <button type="button" class="btn secondary" (click)="loadRecommendations()"
                [disabled]="isLoadingRecommendations()">
                <app-icon name="star" [size]="18" />
                {{ isLoadingRecommendations() ? 'Pensando…' : 'Recomendaciones' }}
              </button>
              <button type="button" class="icon-btn" title="Eliminar playlist" aria-label="Eliminar playlist"
                (click)="remove(pl)" [disabled]="isDeleting()">
                <app-icon name="trash" />
              </button>
            </div>

            @if (pl.canciones.length === 0) {
              <p class="muted">Esta playlist está vacía.</p>
            } @else {
              <table>
                <thead>
                  <tr>
                    <th class="num">#</th>
                    <th>Título</th>
                    <th class="hide-sm">Álbum</th>
                    <th class="hide-sm">Género</th>
                    <th class="hide-sm">Año</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows(); track row.song.id ?? row.index) {
                    @let song = row.song;
                    <tr [class.selected]="row.isCurrent" (click)="select(song)" tabindex="0"
                      (keydown.enter)="select(song)">
                      <td class="num">
                        @if (row.isCurrent) {
                          <app-equalizer [playing]="false" />
                        } @else {
                          {{ row.index + 1 }}
                        }
                      </td>
                      <td>
                        <div class="song">
                          <app-cover [seed]="song.titulo + song.artista" [size]="40" />
                          <div>
                            <strong>{{ song.titulo }}</strong>
                            <small>{{ song.artista }}</small>
                          </div>
                        </div>
                      </td>
                      <td class="hide-sm muted">{{ song.album || '—' }}</td>
                      <td class="hide-sm muted">{{ song.genero || '—' }}</td>
                      <td class="hide-sm muted">{{ song.anno || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }

            @if (isLoadingRecommendations() || recs() || recsError()) {
              <section class="recs">
                <h2>Recomendaciones</h2>
                @if (isLoadingRecommendations()) {
                  <div class="rec-row">
                    @for (i of [1, 2, 3, 4, 5]; track i) {
                      <div class="sk-card">
                        <app-skeleton height="140px" />
                        <app-skeleton width="80%" height="14px" />
                        <app-skeleton width="50%" height="12px" />
                      </div>
                    }
                  </div>
                } @else if (recsError()) {
                  <p class="muted">{{ recsError() }}</p>
                } @else if (recs(); as response) {
                  @if (response.fallback) {
                    <app-fallback-banner [mensaje]="response.mensaje ?? 'Se muestran sugerencias genéricas.'"
                      [motivo]="response.motivoFallback" />
                  }
                  @if (response.recomendaciones.length === 0) {
                    <p class="muted">{{ response.mensaje ?? 'Sin recomendaciones.' }}</p>
                  } @else {
                    <div class="rec-row">
                      @for (item of response.recomendaciones; track item.titulo + item.artista) {
                        <app-track-card [titulo]="item.titulo" [artista]="item.artista"
                          [subtitle]="item.razon ?? item.genero ?? item.artista" />
                      }
                    </div>
                    @if (response.fuente) {
                      <small class="muted">Generado por {{ response.fuente }}</small>
                    }
                  }
                }
              </section>
            }
          </div>
        }
      }
    }
  `,
  styles: `
    :host {
      display: block;
      margin: 0 -24px;
    }
    .hero {
      display: flex;
      align-items: flex-end;
      gap: 24px;
      padding: 72px 24px 24px;
      margin-top: -64px; /* sit behind the translucent top bar */
      flex-wrap: wrap;
    }
    .cover-box {
      width: 200px;
      flex: none;
    }
    .hero-text {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-width: 0;
    }
    small {
      font-weight: 700;
    }
    h1 {
      font-size: clamp(32px, 6vw, 72px);
      font-weight: 900;
      line-height: 1.05;
      overflow-wrap: anywhere;
    }
    .desc,
    .meta {
      color: var(--text-muted);
    }
    .body {
      padding: 24px;
      background: rgba(0, 0, 0, 0.2);
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .actions {
      display: flex;
      align-items: center;
      gap: 24px;
      flex-wrap: wrap;
    }
    .play {
      display: grid;
      place-items: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--accent);
      color: #000;
    }
    .play:disabled {
      opacity: 0.5;
    }
    .icon-btn {
      color: var(--text-muted);
      line-height: 0;
      padding: 8px;
      border-radius: 50%;
    }
    .icon-btn:hover:not(:disabled) {
      color: var(--text);
      background: var(--card-hover);
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th {
      text-align: left;
      color: var(--text-muted);
      font-weight: 400;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      padding: 8px 12px;
      border-bottom: 1px solid #2a2a2a;
    }
    td {
      padding: 8px 12px;
    }
    tbody tr {
      cursor: pointer;
    }
    tbody tr:hover {
      background: rgba(255, 255, 255, 0.1);
    }
    tbody tr.selected strong {
      color: var(--accent);
    }
    .num {
      width: 48px;
      text-align: center;
      color: var(--text-muted);
    }
    .song {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }
    .song div:last-child {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .song small {
      color: var(--text-muted);
      font-weight: 400;
    }
    .muted {
      color: var(--text-muted);
    }
    .recs {
      margin-top: 24px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .rec-row {
      display: flex;
      gap: 16px;
      overflow-x: auto;
      padding-bottom: 8px;
    }
    .sk-card {
      flex: 0 0 180px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 16px;
      background: var(--card);
      border-radius: var(--radius);
    }
    .state {
      padding: 64px 24px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
    @media (max-width: 800px) {
      .hide-sm {
        display: none;
      }
    }
  `,
})
export class PlaylistPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly playlists = inject(PlaylistService);
  private readonly player = inject(PlayerService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly status = signal<Status>('loading');
  protected readonly playlist = signal<Playlist | null>(null);
  protected readonly errorMessage = signal('');
  protected readonly color = signal('#333');
  protected readonly isDeleting = signal(false);

  /** One row per song with its 'is the selected track' flag computed once, not per change detection. */
  protected readonly rows = computed(() => {
    const current = this.player.current();
    return (this.playlist()?.canciones ?? []).map((song, index) => ({
      song,
      index,
      isCurrent: !!current && current.titulo === song.titulo && current.artista === song.artista,
    }));
  });

  protected readonly recs = signal<RecommendationResponse | null>(null);
  protected readonly isLoadingRecommendations = signal(false);
  protected readonly recsError = signal('');

  constructor() {
    this.route.paramMap
      .pipe(
        tap(() => {
          this.status.set('loading');
          this.recs.set(null);
          this.recsError.set('');
        }),
        // Errors are caught per request so the stream survives and keeps reacting to route changes.
        switchMap((params) =>
          this.playlists.get(params.get('nombre') ?? '').pipe(
            map((pl) => ({ pl, error: null as unknown })),
            catchError((error: unknown) => of({ pl: null, error })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => (result.pl ? this.onLoaded(result.pl) : this.onLoadError(result.error)));
  }

  protected select(song: Cancion): void {
    this.player.select({ titulo: song.titulo, artista: song.artista, album: song.album });
  }

  protected loadRecommendations(): void {
    const pl = this.playlist();
    if (!pl) {
      return;
    }
    this.isLoadingRecommendations.set(true);
    this.recsError.set('');
    this.playlists.recommendations(pl.nombre).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.recs.set(response);
        this.isLoadingRecommendations.set(false);
      },
      error: (error: unknown) => {
        this.recsError.set(isApiError(error) ? error.mensaje : 'No se pudieron cargar las recomendaciones.');
        this.isLoadingRecommendations.set(false);
      },
    });
  }

  protected remove(pl: Playlist): void {
    if (!confirm(`¿Eliminar la playlist «${pl.nombre}»? Esta acción no se puede deshacer.`)) {
      return;
    }
    this.isDeleting.set(true);
    this.playlists.remove(pl.nombre).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.toast.success(`Playlist «${pl.nombre}» eliminada.`);
        void this.router.navigate(['/']);
      },
      error: (error: unknown) => {
        this.isDeleting.set(false);
        if (isApiError(error) && error.status === 404) {
          this.status.set('notfound');
        } else if (isApiError(error) && error.status < 500 && error.status !== 429) {
          this.toast.error(error.mensaje);
        }
      },
    });
  }

  private onLoaded(pl: Playlist): void {
    this.playlist.set(pl);
    this.color.set(headerColor(pl.nombre));
    this.status.set('ready');
  }

  private onLoadError(error: unknown): void {
    if (isApiError(error) && error.status === 404) {
      this.status.set('notfound');
      return;
    }
    this.errorMessage.set(isApiError(error) ? error.mensaje : 'Error inesperado.');
    this.status.set('error');
  }
}
