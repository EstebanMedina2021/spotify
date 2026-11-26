import { Injectable, signal } from '@angular/core';

export interface PlayerTrack {
  titulo: string;
  artista: string;
  album?: string | null;
}

/**
 * Holds the track shown in the player bar. There is no audio source in this app (the API stores
 * no audio and Spotify previews are not available), so selecting a track never plays anything.
 */
@Injectable({ providedIn: 'root' })
export class PlayerService {
  readonly isAudioAvailable = false;
  readonly current = signal<PlayerTrack | null>(null);

  select(track: PlayerTrack): void {
    this.current.set(track);
  }

  isCurrent(track: PlayerTrack): boolean {
    const current = this.current();
    return !!current && current.titulo === track.titulo && current.artista === track.artista;
  }
}
