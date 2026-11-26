import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PlaylistService } from '../core/playlist.service';
import { Cover } from '../shared/cover';
import { Icon } from '../shared/icon';
import { Skeleton } from '../shared/skeleton';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Icon, Cover, Skeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="block nav" aria-label="Principal">
      <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
        <app-icon name="home" /> <span>Inicio</span>
      </a>
      <a routerLink="/search" routerLinkActive="active">
        <app-icon name="search" /> <span>Buscar</span>
      </a>
    </nav>

    <section class="block library" aria-label="Tu biblioteca">
      <header>
        <span class="title"><app-icon name="library" /> <span>Tu Biblioteca</span></span>
        <a routerLink="/lists/new" class="add" title="Crear playlist" aria-label="Crear playlist">
          <app-icon name="plus" [size]="20" />
        </a>
      </header>

      <div class="list">
        @if (!playlists.loaded()) {
          @for (i of [1, 2, 3, 4]; track i) {
            <div class="item">
              <app-skeleton width="48px" height="48px" />
              <div class="skel-text">
                <app-skeleton width="70%" height="12px" />
                <app-skeleton width="40%" height="10px" />
              </div>
            </div>
          }
        } @else {
          @for (playlist of playlists.playlists(); track playlist.id) {
            <a class="item" [routerLink]="['/lists', playlist.nombre]" routerLinkActive="current">
              <app-cover [seed]="playlist.nombre" [size]="48" />
              <div class="meta">
                <strong>{{ playlist.nombre }}</strong>
                <small>Playlist · {{ playlist.numeroCanciones }}
                  {{ playlist.numeroCanciones === 1 ? 'canción' : 'canciones' }}</small>
              </div>
            </a>
          } @empty {
            <div class="empty">
              <p><strong>Crea tu primera playlist</strong></p>
              <p>Es muy fácil, te ayudaremos.</p>
              <a class="btn primary" routerLink="/lists/new">Crear playlist</a>
            </div>
          }
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-height: 0;
    }
    .block {
      background: var(--bg);
      border-radius: var(--radius);
    }
    .nav {
      padding: 8px 12px;
      display: flex;
      flex-direction: column;
    }
    .nav a {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 10px 12px;
      font-weight: 700;
      color: var(--text-muted);
      transition: color 0.15s;
    }
    .nav a:hover,
    .nav a.active {
      color: var(--text);
    }
    .library {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 16px 8px 20px;
      color: var(--text-muted);
      font-weight: 700;
    }
    .title {
      display: inline-flex;
      align-items: center;
      gap: 12px;
    }
    .add {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
    }
    .add:hover {
      background: var(--card-hover);
      color: var(--text);
    }
    .list {
      flex: 1;
      overflow-y: auto;
      padding: 0 8px 8px;
    }
    .item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px;
      border-radius: 6px;
    }
    a.item:hover,
    a.item.current {
      background: var(--card);
    }
    .meta {
      min-width: 0;
      display: flex;
      flex-direction: column;
    }
    .meta strong,
    .meta small {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    small {
      color: var(--text-muted);
    }
    .skel-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .empty {
      background: var(--card);
      border-radius: var(--radius);
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      align-items: flex-start;
      margin: 8px;
    }
    .empty .btn {
      margin-top: 8px;
      padding: 6px 16px;
      background: var(--text);
      color: #000;
    }
    @media (max-width: 800px) {
      span:not(.title),
      .meta,
      .empty,
      .title span {
        display: none;
      }
      .nav a {
        justify-content: center;
        padding: 12px 0;
      }
      header {
        justify-content: center;
        padding: 12px 0;
      }
      .title {
        display: none;
      }
    }
  `,
})
export class Sidebar {
  protected readonly playlists = inject(PlaylistService);
}
