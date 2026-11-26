import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PlayerService } from '../core/player.service';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';

const NO_AUDIO = 'Reproducción no disponible: esta versión no incluye una fuente de audio.';

/**
 * Player bar. It shows the selected track, but every control is disabled on purpose: there is no
 * audio source (the API stores no audio and Spotify previews are not available).
 */
@Component({
  selector: 'app-player-bar',
  imports: [Icon, Cover],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer>
      <div class="left">
        @if (player.current(); as track) {
          <app-cover [seed]="track.titulo + track.artista" [size]="56" />
          <div class="info">
            <strong>{{ track.titulo }}</strong>
            <small>{{ track.artista }}</small>
          </div>
        } @else {
          <small class="idle">Selecciona una canción</small>
        }
        <span class="wrap" [title]="noAudio">
          <button type="button" disabled aria-label="Me gusta"><app-icon name="heart" [size]="20" /></button>
        </span>
      </div>

      <div class="center" [title]="noAudio">
        <div class="controls">
          <button type="button" disabled aria-label="Aleatorio"><app-icon name="shuffle" [size]="20" /></button>
          <button type="button" disabled aria-label="Anterior"><app-icon name="prev" /></button>
          <button type="button" class="play" disabled aria-label="Reproducir"><app-icon name="play" /></button>
          <button type="button" disabled aria-label="Siguiente"><app-icon name="next" /></button>
          <button type="button" disabled aria-label="Repetir"><app-icon name="repeat" [size]="20" /></button>
        </div>
        <div class="progress">
          <span>0:00</span>
          <input type="range" min="0" max="100" value="0" disabled aria-label="Progreso" />
          <span>0:00</span>
        </div>
      </div>

      <div class="right" [title]="noAudio">
        <button type="button" disabled aria-label="Cola de reproducción"><app-icon name="queue" [size]="20" /></button>
        <button type="button" disabled aria-label="Silenciar"><app-icon name="volume" [size]="20" /></button>
        <input type="range" min="0" max="100" value="70" disabled aria-label="Volumen" />
      </div>
    </footer>
  `,
  styles: `
    footer {
      height: 100%;
      display: grid;
      grid-template-columns: minmax(180px, 30%) 1fr minmax(180px, 30%);
      align-items: center;
      gap: 16px;
      padding: 0 8px;
    }
    .left {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }
    .info {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .info strong,
    .info small {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    small {
      color: var(--text-muted);
    }
    .idle {
      flex: 1;
    }
    button {
      color: var(--text-muted);
      line-height: 0;
      padding: 6px;
    }
    button:disabled {
      opacity: 0.45;
    }
    .center {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      max-width: 722px;
      width: 100%;
      margin: 0 auto;
    }
    .controls {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .play {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #fff;
      color: #000;
    }
    .progress {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      font-size: 11px;
      color: var(--text-muted);
    }
    .progress input {
      flex: 1;
    }
    .right {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .right input {
      width: 100px;
    }
    input[type='range'] {
      accent-color: var(--accent);
      height: 4px;
    }
    @media (max-width: 800px) {
      footer {
        grid-template-columns: 1fr auto;
      }
      .right {
        display: none;
      }
      .progress,
      .controls button:not(.play) {
        display: none;
      }
    }
  `,
})
export class PlayerBar {
  protected readonly player = inject(PlayerService);
  protected readonly noAudio = NO_AUDIO;
}
