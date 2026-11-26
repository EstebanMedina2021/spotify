import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PlaylistService } from '../core/playlist.service';
import { PlayerBar } from './player-bar';
import { Sidebar } from './sidebar';
import { Topbar } from './topbar';

/** Fixed 3-panel layout: sidebar, scrollable main area and bottom player bar. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Sidebar, Topbar, PlayerBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app">
      <app-sidebar class="sidebar" />
      <main class="main">
        <app-topbar />
        <div class="content"><router-outlet /></div>
      </main>
      <div class="player"><app-player-bar /></div>
    </div>
  `,
  styles: `
    .app {
      height: 100vh;
      display: grid;
      grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
      grid-template-rows: minmax(0, 1fr) var(--player-height);
      grid-template-areas:
        'sidebar main'
        'player player';
      gap: 8px;
      padding: 8px 8px 0;
    }
    .sidebar {
      grid-area: sidebar;
    }
    .main {
      grid-area: main;
      background: var(--bg);
      border-radius: var(--radius);
      overflow-y: auto;
      position: relative;
    }
    .content {
      padding: 0 24px 32px;
    }
    .player {
      grid-area: player;
    }
    @media (max-width: 800px) {
      .app {
        grid-template-columns: 72px minmax(0, 1fr);
      }
      .content {
        padding: 0 16px 24px;
      }
    }
  `,
})
export class Shell implements OnInit {
  private readonly playlists = inject(PlaylistService);

  ngOnInit(): void {
    this.playlists.refresh();
  }
}
