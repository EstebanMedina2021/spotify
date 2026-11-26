import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';
import { Cancion, isApiError, SpotifyGenres, SpotifyTrack } from '../core/models';
import { PlaylistService } from '../core/playlist.service';
import { SpotifyService } from '../core/spotify.service';
import { ToastService } from '../core/toast.service';
import { noControlChars, notBlank, playlistNameChars } from '../core/validators';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';
import { Skeleton } from '../shared/skeleton';

const MAX_SONGS = 100;

@Component({
  selector: 'app-create-playlist-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, Cover, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="page-title">Crear playlist</h1>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="layout">
      <section class="panel">
        <div class="field">
          <label for="nombre">Nombre</label>
          <input id="nombre" formControlName="nombre" maxlength="50" autocomplete="off"
            [class.invalid]="invalid(form.controls.nombre)" />
          @if (invalid(form.controls.nombre)) {
            <span class="error">
              @if (form.controls.nombre.errors?.['required'] || form.controls.nombre.errors?.['blank']) {
                El nombre es obligatorio.
              } @else if (form.controls.nombre.errors?.['forbiddenChars']) {
                El nombre no puede contener / \\ ? # %.
              } @else if (form.controls.nombre.errors?.['controlChars']) {
                El nombre contiene caracteres no permitidos.
              } @else if (form.controls.nombre.errors?.['duplicate']) {
                Ya tienes una playlist con ese nombre.
              } @else {
                Máximo 50 caracteres.
              }
            </span>
          }
        </div>

        <div class="field">
          <label for="descripcion">Descripción (opcional)</label>
          <textarea id="descripcion" rows="3" formControlName="descripcion" maxlength="200"
            [class.invalid]="invalid(form.controls.descripcion)"></textarea>
          @if (invalid(form.controls.descripcion)) {
            <span class="error">Máximo 200 caracteres y sin caracteres de control.</span>
          }
        </div>

        @if (errorMessage()) {
          <p class="error banner" role="alert">{{ errorMessage() }}</p>
        }

        <div class="buttons">
          <button type="submit" class="btn primary" [disabled]="submitting()">
            {{ submitting() ? 'Guardando…' : 'Crear playlist' }}
          </button>
          <a class="btn secondary" routerLink="/">Cancelar</a>
        </div>
      </section>

      <section class="panel">
        <h2>Canciones <small>({{ songs().length }}/{{ maxSongs }})</small></h2>

        @if (songs().length === 0) {
          <p class="muted">Aún no agregaste canciones. Busca en Spotify o agrégalas a mano.</p>
        } @else {
          <ul class="songs">
            @for (song of songs(); track $index) {
              <li>
                <app-cover [seed]="song.titulo + song.artista" [size]="40" />
                <span class="info">
                  <strong>{{ song.titulo }}</strong>
                  <small>{{ song.artista }}{{ song.album ? ' · ' + song.album : '' }}</small>
                </span>
                <button type="button" class="icon-btn" [attr.aria-label]="'Quitar ' + song.titulo"
                  (click)="removeSong($index)">
                  <app-icon name="close" [size]="18" />
                </button>
              </li>
            }
          </ul>
        }

        <div class="field">
          <label for="spotify-q">Buscar en Spotify</label>
          <input id="spotify-q" type="search" [formControl]="songQuery" maxlength="100" autocomplete="off"
            placeholder="Título o artista" [disabled]="songs().length >= maxSongs" />
        </div>

        @if (searching()) {
          <app-skeleton height="40px" />
          <app-skeleton height="40px" />
        } @else if (searchFailed()) {
          <p class="muted">No se pudo buscar en Spotify. Puedes agregar la canción a mano.</p>
        } @else if (results().length > 0) {
          <ul class="songs results">
            @for (track of results(); track track.spotifyId) {
              <li>
                <app-cover [seed]="track.titulo + track.artista" [size]="40" />
                <span class="info">
                  <strong>{{ track.titulo }}</strong>
                  <small>{{ track.artista }}{{ track.album ? ' · ' + track.album : '' }}</small>
                </span>
                <button type="button" class="btn secondary add" (click)="addFromSpotify(track)"
                  [disabled]="songs().length >= maxSongs">
                  <app-icon name="plus" [size]="16" /> Agregar
                </button>
              </li>
            }
          </ul>
        }

        <details class="manual">
          <summary>Agregar a mano</summary>
          <div class="manual-grid" [formGroup]="manual">
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
            <button type="button" class="btn secondary" (click)="addManual()"
              [disabled]="manual.invalid || songs().length >= maxSongs">
              <app-icon name="plus" [size]="16" /> Agregar canción
            </button>
          </div>
        </details>
      </section>
    </form>
  `,
  styles: `
    .page-title {
      font-size: 32px;
      margin: 8px 0 24px;
    }
    .layout {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 24px;
      align-items: start;
    }
    .panel {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 24px;
      border-radius: var(--radius);
      background: var(--card);
    }
    small,
    .muted {
      color: var(--text-muted);
    }
    .banner {
      padding: 10px 12px;
      border-radius: 4px;
      background: rgba(233, 20, 41, 0.15);
    }
    .buttons {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
    .songs {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-height: 320px;
      overflow-y: auto;
    }
    .songs li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 8px;
      border-radius: 4px;
    }
    .songs li:hover {
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
    .icon-btn {
      color: var(--text-muted);
      line-height: 0;
      padding: 6px;
    }
    .icon-btn:hover {
      color: var(--text);
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
export class CreatePlaylistPage {
  private readonly playlists = inject(PlaylistService);
  private readonly spotify = inject(SpotifyService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly maxSongs = MAX_SONGS;

  protected readonly form = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(50), notBlank, noControlChars, playlistNameChars]],
    descripcion: ['', [Validators.maxLength(200), noControlChars]],
  });

  protected readonly manual = this.fb.group({
    titulo: ['', [Validators.required, Validators.maxLength(100), notBlank, noControlChars]],
    artista: ['', [Validators.required, Validators.maxLength(100), notBlank, noControlChars]],
    album: ['', [Validators.maxLength(100), noControlChars]],
    anno: ['', [Validators.maxLength(10), noControlChars]],
    genero: ['', [Validators.maxLength(50), noControlChars]],
  });

  protected readonly songs = signal<Cancion[]>([]);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly songQuery = new FormControl('', { nonNullable: true });
  protected readonly results = signal<SpotifyTrack[]>([]);
  protected readonly searching = signal(false);
  protected readonly searchFailed = signal(false);
  protected readonly genres = signal<SpotifyGenres | null>(null);

  constructor() {
    this.spotify.genres().subscribe({ next: (g) => this.genres.set(g), error: () => this.genres.set(null) });

    this.songQuery.valueChanges
      .pipe(
        debounceTime(350),
        map((value) => value.trim()),
        distinctUntilChanged(),
        tap((term) => {
          this.searchFailed.set(false);
          this.searching.set(term.length >= 2);
          if (term.length < 2) {
            this.results.set([]);
          }
        }),
        switchMap((term) =>
          term.length < 2
            ? of({ tracks: [] as SpotifyTrack[], failed: false })
            : this.spotify.search(term).pipe(
                map((tracks) => ({ tracks, failed: false })),
                catchError(() => of({ tracks: [] as SpotifyTrack[], failed: true })),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.results.set(result.tracks);
        this.searchFailed.set(result.failed);
        this.searching.set(false);
      });
  }

  protected invalid(control: { invalid: boolean; touched: boolean; dirty: boolean }): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  protected addFromSpotify(track: SpotifyTrack): void {
    this.pushSong({ titulo: track.titulo, artista: track.artista, album: track.album });
  }

  protected addManual(): void {
    if (this.manual.invalid) {
      this.manual.markAllAsTouched();
      return;
    }
    const value = this.manual.getRawValue();
    this.pushSong({
      titulo: value.titulo,
      artista: value.artista,
      album: value.album,
      anno: value.anno,
      genero: value.genero,
    });
    this.manual.reset();
  }

  protected removeSong(index: number): void {
    this.songs.update((list) => list.filter((_, i) => i !== index));
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set('');
    const { nombre, descripcion } = this.form.getRawValue();
    const nombreTrim = nombre.trim();

    this.playlists
      .create({
        nombre: nombreTrim,
        descripcion: descripcion.trim() || null,
        canciones: this.songs(),
      })
      .subscribe({
        next: (created) => {
          this.toast.success(`Playlist «${created.nombre}» creada.`);
          void this.router.navigate(['/lists', created.nombre]);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (isApiError(error) && error.status === 409) {
            this.form.controls.nombre.setErrors({ duplicate: true });
            this.form.controls.nombre.markAsTouched();
          } else if (isApiError(error) && error.status === 400) {
            this.errorMessage.set(error.mensaje);
          } else if (isApiError(error) && error.status === 403) {
            this.errorMessage.set('Tu sesión de seguridad expiró. Recarga la página e intenta de nuevo.');
          }
          // 401, 429 and 5xx are handled globally by the error interceptor (redirect / toast).
        },
      });
  }

  private pushSong(song: Cancion): void {
    if (this.songs().length >= MAX_SONGS) {
      return;
    }
    const clean: Cancion = {
      titulo: song.titulo.trim(),
      artista: song.artista.trim(),
      album: song.album?.trim() || null,
      anno: song.anno?.trim() || null,
      genero: song.genero?.trim() || null,
    };
    this.songs.update((list) => [...list, clean]);
  }
}
