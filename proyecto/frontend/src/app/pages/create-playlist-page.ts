import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Cancion, isApiError } from '../core/models';
import { PlaylistService } from '../core/playlist.service';
import { ToastService } from '../core/toast.service';
import { noControlChars, notBlank, playlistNameChars } from '../core/validators';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';
import { SongPicker } from './song-picker';

const MAX_SONGS = 100;

@Component({
  selector: 'app-create-playlist-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, Cover, SongPicker],
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
          <button type="submit" class="btn primary" [disabled]="isSubmitting()">
            {{ isSubmitting() ? 'Guardando…' : 'Crear playlist' }}
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

        <app-song-picker [isFull]="songs().length >= maxSongs" (songSelected)="addSong($event)" />
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
  `,
})
export class CreatePlaylistPage {
  private readonly playlists = inject(PlaylistService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly maxSongs = MAX_SONGS;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(50), notBlank, noControlChars, playlistNameChars]],
    descripcion: ['', [Validators.maxLength(200), noControlChars]],
  });

  protected readonly songs = signal<Cancion[]>([]);
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal('');

  protected invalid(control: { invalid: boolean; touched: boolean; dirty: boolean }): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  protected addSong(song: Cancion): void {
    if (this.songs().length >= MAX_SONGS) {
      return;
    }
    this.songs.update((list) => [
      ...list,
      {
        titulo: song.titulo.trim(),
        artista: song.artista.trim(),
        album: song.album?.trim() || null,
        anno: song.anno?.trim() || null,
        genero: song.genero?.trim() || null,
      },
    ]);
  }

  protected removeSong(index: number): void {
    this.songs.update((list) => list.filter((_, i) => i !== index));
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set('');
    const { nombre, descripcion } = this.form.getRawValue();

    this.playlists
      .create({ nombre: nombre.trim(), descripcion: descripcion.trim() || null, canciones: this.songs() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.toast.success(`Playlist «${created.nombre}» creada.`);
          void this.router.navigate(['/lists', created.nombre]);
        },
        error: (error: unknown) => this.onSubmitError(error),
      });
  }

  private onSubmitError(error: unknown): void {
    this.isSubmitting.set(false);
    if (!isApiError(error)) {
      return;
    }
    if (error.status === 409) {
      this.form.controls.nombre.setErrors({ duplicate: true });
      this.form.controls.nombre.markAsTouched();
    } else if (error.status === 400) {
      this.errorMessage.set(error.mensaje);
    } else if (error.status === 403) {
      this.errorMessage.set('Tu sesión de seguridad expiró. Recarga la página e intenta de nuevo.');
    }
    // 401, 429 and 5xx are handled globally by the error interceptor (redirect / toast).
  }
}
