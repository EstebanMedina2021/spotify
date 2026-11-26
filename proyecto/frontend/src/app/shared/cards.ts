import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PlaylistSummary } from '../core/models';
import { PlayerService } from '../core/player.service';
import { Cover } from './cover';

const cardStyles = `
  :host {
    display: block;
    flex: 0 0 180px;
    min-width: 0;
  }
  .card {
    display: block;
    width: 100%;
    text-align: left;
    padding: 16px;
    border-radius: var(--radius);
    background: var(--card);
    transition: background-color 0.2s;
  }
  .card:hover {
    background: var(--card-hover);
  }
  .card.selected h3 {
    color: var(--accent);
  }
  .art {
    margin-bottom: 16px;
  }
  h3 {
    font-size: 15px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  p {
    margin-top: 4px;
    color: var(--text-muted);
    font-size: 13px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
`;

@Component({
  selector: 'app-playlist-card',
  imports: [RouterLink, Cover],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="card" [routerLink]="['/lists', playlist().nombre]">
      <div class="art"><app-cover [seed]="playlist().nombre" /></div>
      <h3>{{ playlist().nombre }}</h3>
      <p>{{ playlist().numeroCanciones }} {{ playlist().numeroCanciones === 1 ? 'canción' : 'canciones' }}</p>
    </a>
  `,
  styles: cardStyles,
})
export class PlaylistCard {
  readonly playlist = input.required<PlaylistSummary>();
}

/** Card for a song (recommendation or Spotify result). Clicking selects it in the player bar. */
@Component({
  selector: 'app-track-card',
  imports: [Cover],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="card" [class.selected]="selected()" (click)="select()">
      <div class="art"><app-cover [seed]="titulo() + artista()" /></div>
      <h3>{{ titulo() }}</h3>
      <p>{{ subtitle() || artista() }}</p>
    </button>
  `,
  styles: cardStyles,
})
export class TrackCard {
  private readonly player = inject(PlayerService);

  readonly titulo = input.required<string>();
  readonly artista = input.required<string>();
  /** Optional second line (e.g. the AI's reason); defaults to the artist. */
  readonly subtitle = input<string | null>(null);

  protected readonly selected = computed(() =>
    this.player.isCurrent({ titulo: this.titulo(), artista: this.artista() }) && !!this.player.current(),
  );

  protected select(): void {
    this.player.select({ titulo: this.titulo(), artista: this.artista() });
  }
}
