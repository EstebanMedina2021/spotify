import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { Cancion, SpotifyGenres, SpotifyTrack } from '../core/models';
import { SpotifyService } from '../core/spotify.service';
import { noControlChars, notBlank } from '../core/validators';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';
import { Skeleton } from '../shared/skeleton';

const MIN_SEARCH_CHARS = 2;

/**
 * Finds songs (Spotify search) or lets the user type one, and emits it. It owns no list: the parent
 * decides what to do with each emitted song.
 */
@Component({
  selector: 'app-song-picker',
  imports: [ReactiveFormsModule, Icon, Cover, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      <label for="spotify-q">Buscar en Spotify</label>
      <input id="spotify-q" type="search" [formControl]="songQuery" maxlength="100" autocomplete="off"
        placeholder="Título o artista" [disabled]="isFull()" (keydown.enter)="$event.preventDefault()" />
    </div>

    @if (isSearching()) {
      <app-skeleton height="40px" />
      <app-skeleton height="40px" />
    } @else if (hasSearchFailed()) {
      <p class="muted">No se pudo buscar en Spotify. Puedes agregar la canción a mano.</p>
    } @else if (results().length > 0) {
      <ul class="results">
        @for (track of results(); track track.spotifyId) {
          <li>
            <app-cover [seed]="track.titulo + track.artista" [size]="40" />
            <span class="info">
              <strong>{{ track.titulo }}</strong>
              <small>{{ track.artista }}{{ track.album ? ' · ' + track.album : '' }}</small>
            </span>
            <button type="button" class="btn secondary add" (click)="addFromSpotify(track)" [disabled]="isFull()">
              <app-icon name="plus" [size]="16" /> Agregar
            </button>
          </li>
        }
      </ul>
    }

    <details class="manual">
      <summary>Agregar a mano</summary>
      <!-- Enter inside the parent <form> must not submit the whole playlist -->
      <div class="manual-grid" [formGroup]="manual" (keydown.enter)="$event.preventDefault()">
        <div class="field">
          <label for="m-titulo">Título *</label>
          <input id="m-titulo" formControlName="titulo" maxlength="100" />
        </div>
        <div class="field">
          <label for="m-artista">Artista *</label>
          <input id="m-artista" formControlName="artista" maxlength="100" />
        </div>
        <div class="field">
          <label for="m-album">Álbum</label>
          <input id="m-album" formControlName="album" maxlength="100" />
        </div>
        <div class="field">
          <label for="m-anno">Año</label>
          <input id="m-anno" formControlName="anno" maxlength="10" />
        </div>
        <div class="field">
          <label for="m-genero">Género</label>
          <input id="m-genero" formControlName="genero" maxlength="50" list="genre-options" />
          <datalist id="genre-options">
            @for (genre of genres()?.generos ?? []; track genre) {
              <option [value]="genre"></option>
            }
          </datalist>
          <span class="hint">Sugerencias de Spotify; también puedes escribir otro.</span>
        </div>
        <button type="button" class="btn secondary" (click)="addManual()" [disabled]="manual.invalid || isFull()">
          <app-icon name="plus" [size]="16" /> Agregar canción
        </button>
      </div>
    </details>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .muted {
      color: var(--text-muted);
    }
    small {
      color: var(--text-muted);
    }
    .results {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-height: 320px;
      overflow-y: auto;
    }
    .results li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 8px;
      border-radius: 4px;
    }
    .results li:hover {
      background: rgba(255, 255, 255, 0.08);
    }
    .info {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }
    .info strong,
    .info small {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .add {
      padding: 6px 14px;
    }
    .manual summary {
      cursor: pointer;
      font-weight: 700;
      margin-bottom: 12px;
    }
    .manual-grid {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
  `,
})
export class SongPicker {
  private readonly spotify = inject(SpotifyService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** True when the list the parent builds cannot take more songs. */
  readonly isFull = input(false);
  readonly songSelected = output<Cancion>();

  protected readonly songQuery = new FormControl('', { nonNullable: true });
  protected readonly results = signal<SpotifyTrack[]>([]);
  protected readonly isSearching = signal(false);
  protected readonly hasSearchFailed = signal(false);
  protected readonly genres = signal<SpotifyGenres | null>(null);

  protected readonly manual = this.fb.group({
    titulo: ['', [Validators.required, Validators.maxLength(100), notBlank, noControlChars]],
    artista: ['', [Validators.required, Validators.maxLength(100), notBlank, noControlChars]],
    album: ['', [Validators.maxLength(100), noControlChars]],
    anno: ['', [Validators.maxLength(10), noControlChars]],
    genero: ['', [Validators.maxLength(50), noControlChars]],
  });

  constructor() {
    this.spotify
      .genres()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (g) => this.genres.set(g), error: () => this.genres.set(null) });

    this.songQuery.valueChanges
      .pipe(
        debounceTime(350),
        map((value) => value.trim()),
        distinctUntilChanged(),
        tap((term) => {
          this.hasSearchFailed.set(false);
          this.isSearching.set(term.length >= MIN_SEARCH_CHARS);
          if (term.length < MIN_SEARCH_CHARS) {
            this.results.set([]);
          }
        }),
        // switchMap cancels the previous request, so a slow old answer never overwrites a newer one.
        switchMap((term) =>
          term.length < MIN_SEARCH_CHARS
            ? of({ tracks: [] as SpotifyTrack[], hasFailed: false })
            : this.spotify.search(term).pipe(
                map((tracks) => ({ tracks, hasFailed: false })),
                catchError(() => of({ tracks: [] as SpotifyTrack[], hasFailed: true })),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.results.set(result.tracks);
        this.hasSearchFailed.set(result.hasFailed);
        this.isSearching.set(false);
      });
  }

  protected addFromSpotify(track: SpotifyTrack): void {
    this.songSelected.emit({ titulo: track.titulo, artista: track.artista, album: track.album });
  }

  protected addManual(): void {
    if (this.manual.invalid) {
      this.manual.markAllAsTouched();
      return;
    }
    this.songSelected.emit(this.manual.getRawValue());
    this.manual.reset();
  }
}
