import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { isApiError, RecommendationResponse, SpotifyGenres } from '../core/models';
import { PlaylistService } from '../core/playlist.service';
import { SpotifyService } from '../core/spotify.service';
import { greetingFor, hueFromString } from '../core/util';
import { Carousel } from '../shared/carousel';
import { PlaylistCard, TrackCard } from '../shared/cards';
import { Cover } from '../shared/cover';
import { FallbackBanner } from '../shared/fallback-banner';
import { Skeleton } from '../shared/skeleton';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, Carousel, PlaylistCard, TrackCard, Cover, FallbackBanner, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="greeting">{{ greeting }}</h1>

    <div class="quick">
      @if (!playlists.loaded()) {
        @for (i of [1, 2, 3, 4, 5, 6]; track i) {
          <app-skeleton height="64px" radius="4px" />
        }
      } @else {
        @for (playlist of quickAccess(); track playlist.id) {
          <a class="tile" [routerLink]="['/lists', playlist.nombre]">
            <app-cover [seed]="playlist.nombre" [size]="64" />
            <span>{{ playlist.nombre }}</span>
          </a>
        }
      }
    </div>

    @if (playlists.loaded() && playlists.playlists().length === 0) {
      <section class="empty">
        <h2>Aún no tienes playlists</h2>
        <p>Crea una y llénala con canciones de Spotify para recibir recomendaciones.</p>
        <a class="btn primary" routerLink="/lists/new">Crear playlist</a>
      </section>
    } @else if (playlists.loaded()) {
      <app-carousel title="Tus playlists">
        @for (playlist of playlists.playlists(); track playlist.id) {
          <app-playlist-card [playlist]="playlist" />
        }
      </app-carousel>
    }

    @if (basePlaylist()) {
      <app-carousel title="Recomendado para ti" [subtitle]="'Basado en «' + basePlaylist() + '»'">
        @if (recsLoading()) {
          @for (i of [1, 2, 3, 4, 5]; track i) {
            <div class="sk-card">
              <app-skeleton height="140px" />
              <app-skeleton width="80%" height="14px" />
              <app-skeleton width="50%" height="12px" />
            </div>
          }
        } @else if (recs(); as response) {
          @for (item of response.recomendaciones; track item.titulo + item.artista) {
            <app-track-card [titulo]="item.titulo" [artista]="item.artista" [subtitle]="item.razon ?? item.artista" />
          } @empty {
            <p class="muted">{{ response.mensaje ?? 'Sin recomendaciones por ahora.' }}</p>
          }
        } @else {
          <p class="muted">{{ recsError() }}</p>
        }
      </app-carousel>
      @if (recs()?.fallback) {
        <div class="banner">
          <app-fallback-banner [mensaje]="recs()!.mensaje ?? 'Se muestran sugerencias genéricas.'"
            [motivo]="recs()!.motivoFallback" />
        </div>
      }
    }

    <app-carousel title="Explora géneros" subtitle="Géneros musicales de Spotify">
      @if (!genres()) {
        @for (i of [1, 2, 3, 4, 5, 6]; track i) {
          <app-skeleton width="180px" height="100px" radius="8px" />
        }
      } @else {
        @for (genre of genres()!.generos; track genre) {
          <a class="genre" [routerLink]="['/search']" [queryParams]="{ q: genre }"
            [style.background]="genreColor(genre)">
            {{ genre }}
          </a>
        }
      }
    </app-carousel>
  `,
  styles: `
    .greeting {
      font-size: 32px;
      margin: 8px 0 24px;
    }
    .quick {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
    }
    .tile {
      display: flex;
      align-items: center;
      gap: 16px;
      padding-right: 16px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.08);
      font-weight: 700;
      overflow: hidden;
      transition: background-color 0.2s;
    }
    .tile:hover {
      background: rgba(255, 255, 255, 0.18);
    }
    .tile span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .empty {
      margin-top: 40px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      align-items: flex-start;
    }
    .muted {
      color: var(--text-muted);
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
    .banner {
      margin-top: 8px;
    }
    .genre {
      flex: 0 0 180px;
      height: 100px;
      padding: 14px;
      border-radius: 8px;
      font-weight: 700;
      font-size: 18px;
      text-transform: capitalize;
      transition: transform 0.15s;
    }
    .genre:hover {
      transform: scale(1.03);
    }
  `,
})
export class HomePage {
  protected readonly playlists = inject(PlaylistService);
  private readonly spotify = inject(SpotifyService);

  protected readonly greeting = greetingFor(new Date().getHours());

  protected readonly quickAccess = computed(() => this.playlists.playlists().slice(0, 6));
  protected readonly genres = signal<SpotifyGenres | null>(null);

  /** First playlist that has songs: the AI needs something to analyze. */
  protected readonly basePlaylist = signal<string | null>(null);
  protected readonly recs = signal<RecommendationResponse | null>(null);
  protected readonly recsLoading = signal(false);
  protected readonly recsError = signal('');

  constructor() {
    this.spotify.genres().subscribe({ next: (g) => this.genres.set(g), error: () => this.genres.set({ generos: [], fallback: true, motivoFallback: null }) });

    effect(() => {
      const loaded = this.playlists.loaded();
      const candidate = this.playlists.playlists().find((p) => p.numeroCanciones > 0)?.nombre ?? null;
      if (loaded) {
        untracked(() => this.loadRecommendations(candidate));
      }
    });
  }

  protected genreColor(genre: string): string {
    return `hsl(${hueFromString(genre)} 55% 32%)`;
  }

  private loadRecommendations(nombre: string | null): void {
    if (nombre === this.basePlaylist() && (this.recs() || this.recsLoading())) {
      return;
    }
    this.basePlaylist.set(nombre);
    this.recs.set(null);
    this.recsError.set('');
    if (!nombre) {
      return;
    }
    this.recsLoading.set(true);
    this.playlists.recommendations(nombre).subscribe({
      next: (response) => {
        this.recs.set(response);
        this.recsLoading.set(false);
      },
      error: (error: unknown) => {
        this.recsError.set(isApiError(error) ? error.mensaje : 'No se pudieron cargar las recomendaciones.');
        this.recsLoading.set(false);
      },
    });
  }
}
