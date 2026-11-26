import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { SpotifyGenres, SpotifyTrack } from '../core/models';
import { PlayerService } from '../core/player.service';
import { PlaylistService } from '../core/playlist.service';
import { SpotifyService } from '../core/spotify.service';
import { hueFromString } from '../core/util';
import { PlaylistCard } from '../shared/cards';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';
import { Skeleton } from '../shared/skeleton';

const MIN_CHARS = 2;
const MAX_CHARS = 100; // backend limit for `q`

@Component({
  selector: 'app-search-page',
  imports: [ReactiveFormsModule, Icon, Cover, PlaylistCard, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="searchbar">
      <app-icon name="search" [size]="20" />
      <input type="search" [formControl]="query" [maxlength]="maxChars" placeholder="¿Qué quieres escuchar?"
        aria-label="Buscar canciones, artistas y playlists" autocomplete="off" />
    </div>

    @if (term().length < minChars) {
      <h2 class="section-title">Explorar todo</h2>
      <div class="genres">
        @if (!genres()) {
          @for (i of [1, 2, 3, 4, 5, 6, 7, 8]; track i) {
            <app-skeleton height="100px" radius="8px" />
          }
        } @else {
          @for (card of genreCards(); track card.name) {
            <button type="button" class="genre" [style.background]="card.color" (click)="query.setValue(card.name)">
              {{ card.name }}
            </button>
          }
        }
      </div>
    } @else {
      @if (matchingPlaylists().length > 0) {
        <h2 class="section-title">Tus playlists</h2>
        <div class="row">
          @for (playlist of matchingPlaylists(); track playlist.id) {
            <app-playlist-card [playlist]="playlist" />
          }
        </div>
      }

      @if (isSearching()) {
        <h2 class="section-title">Canciones</h2>
        @for (i of [1, 2, 3, 4]; track i) {
          <div class="sk-row"><app-skeleton width="40px" height="40px" /><app-skeleton width="50%" height="14px" /></div>
        }
      } @else {
        @if (artists().length > 0) {
          <h2 class="section-title">Artistas</h2>
          <div class="row">
            @for (artist of artists(); track artist) {
              <button type="button" class="artist" (click)="query.setValue(artist)">
                <app-cover [seed]="artist" [round]="true" />
                <strong>{{ artist }}</strong>
                <small>Artista</small>
              </button>
            }
          </div>
        }

        <h2 class="section-title">Canciones</h2>
        @if (hasFailed()) {
          <p class="muted">No se pudo buscar en Spotify en este momento.</p>
        } @else if (tracks().length === 0) {
          <p class="muted">Sin resultados para «{{ term() }}».</p>
        } @else {
          <ul class="tracks">
            @for (row of trackRows(); track row.track.spotifyId) {
              @let track = row.track;
              <li>
                <button type="button" class="track" [class.selected]="row.isCurrent" (click)="select(track)">
                  <app-cover [seed]="track.titulo + track.artista" [size]="40" />
                  <span class="t">
                    <strong>{{ track.titulo }}</strong>
                    <small>{{ track.artista }}</small>
                  </span>
                  <span class="muted album">{{ track.album || '' }}</span>
                </button>
              </li>
            }
          </ul>
        }
      }
    }
  `,
  styles: `
    .searchbar {
      display: flex;
      align-items: center;
      gap: 10px;
      width: min(100%, 364px);
      padding: 0 14px;
      border-radius: 500px;
      background: #242424;
      color: var(--text-muted);
      margin: 8px 0 8px;
    }
    .searchbar:focus-within {
      outline: 2px solid var(--text);
    }
    .searchbar input {
      flex: 1;
      padding: 12px 0;
      background: none;
      border: 0;
      outline: none;
    }
    .section-title {
      font-size: 22px;
      margin: 28px 0 14px;
    }
    .genres {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 16px;
    }
    .genre {
      height: 100px;
      padding: 14px;
      border-radius: 8px;
      font-weight: 700;
      font-size: 18px;
      text-align: left;
      text-transform: capitalize;
      transition: transform 0.15s;
    }
    .genre:hover {
      transform: scale(1.03);
    }
    .row {
      display: flex;
      gap: 16px;
      overflow-x: auto;
      padding-bottom: 8px;
    }
    .artist {
      flex: 0 0 160px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px;
      border-radius: var(--radius);
      background: var(--card);
      text-align: left;
    }
    .artist:hover {
      background: var(--card-hover);
    }
    .artist app-cover {
      margin-bottom: 12px;
    }
    .artist strong {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    small,
    .muted {
      color: var(--text-muted);
    }
    .tracks {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .track {
      display: grid;
      grid-template-columns: 40px minmax(0, 2fr) minmax(0, 1fr);
      align-items: center;
      gap: 12px;
      width: 100%;
      padding: 8px 12px;
      border-radius: 4px;
      text-align: left;
    }
    .track:hover {
      background: rgba(255, 255, 255, 0.1);
    }
    .track.selected strong {
      color: var(--accent);
    }
    .t {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .t strong,
    .t small,
    .album {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .sk-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
    }
    @media (max-width: 800px) {
      .album {
        display: none;
      }
      .track {
        grid-template-columns: 40px minmax(0, 1fr);
      }
    }
  `,
})
export class SearchPage {
  private readonly spotify = inject(SpotifyService);
  private readonly playlistService = inject(PlaylistService);
  private readonly player = inject(PlayerService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly minChars = MIN_CHARS;
  protected readonly maxChars = MAX_CHARS;

  protected readonly query = new FormControl('', { nonNullable: true });
  /** Debounced, trimmed search term. */
  protected readonly term = signal('');
  protected readonly tracks = signal<SpotifyTrack[]>([]);
  protected readonly isSearching = signal(false);
  protected readonly hasFailed = signal(false);
  protected readonly genres = signal<SpotifyGenres | null>(null);
  protected readonly genreCards = computed(() =>
    (this.genres()?.generos ?? []).map((name) => ({ name, color: `hsl(${hueFromString(name)} 55% 32%)` })),
  );
  protected readonly trackRows = computed(() =>
    this.tracks().map((track) => ({ track, isCurrent: this.player.isCurrent(track) })),
  );

  protected readonly matchingPlaylists = computed(() => {
    const needle = this.term().toLowerCase();
    return needle.length < MIN_CHARS
      ? []
      : this.playlistService.playlists().filter((p) => p.nombre.toLowerCase().includes(needle));
  });

  /** Artists are derived from the track results (a track's artist field may hold several names). */
  protected readonly artists = computed(() => {
    const names = new Set<string>();
    for (const track of this.tracks()) {
      track.artista
        .split(',')
        .map((name) => name.trim())
        .filter(Boolean)
        .forEach((name) => names.add(name));
    }
    return [...names].slice(0, 8);
  });

  constructor() {
    this.spotify.genres().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (g) => this.genres.set(g), error: () => this.genres.set(null) });

    this.query.valueChanges
      .pipe(
        debounceTime(350),
        map((value) => value.trim()),
        distinctUntilChanged(),
        tap((term) => {
          this.term.set(term);
          this.hasFailed.set(false);
          this.isSearching.set(term.length >= MIN_CHARS);
          if (term.length < MIN_CHARS) {
            this.tracks.set([]);
          }
        }),
        // switchMap cancels the previous request, so a slow old answer never overwrites a newer one.
        switchMap((term) =>
          term.length < MIN_CHARS
            ? of({ tracks: [] as SpotifyTrack[], hasFailed: false })
            : this.spotify.search(term).pipe(
                map((tracks) => ({ tracks, hasFailed: false })),
                catchError(() => of({ tracks: [] as SpotifyTrack[], hasFailed: true })),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.tracks.set(result.tracks);
        this.hasFailed.set(result.hasFailed);
        this.isSearching.set(false);
      });

    const initial = this.route.snapshot.queryParamMap.get('q');
    if (initial) {
      this.query.setValue(initial.slice(0, MAX_CHARS));
    }
  }

  protected select(track: SpotifyTrack): void {
    this.player.select({ titulo: track.titulo, artista: track.artista, album: track.album });
  }
}
