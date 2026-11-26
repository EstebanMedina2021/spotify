import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Animated bars shown next to the current track. They only animate while `playing` is true;
 * this app has no audio, so callers pass `false` and the bars stay still.
 */
@Component({
  selector: 'app-equalizer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="eq" [class.playing]="playing()" aria-hidden="true"><i></i><i></i><i></i></span>`,
  styles: `
    .eq {
      display: inline-flex;
      align-items: flex-end;
      gap: 2px;
      height: 14px;
      width: 14px;
    }
    i {
      flex: 1;
      background: var(--accent);
      height: 40%;
    }
    i:nth-child(2) {
      height: 85%;
    }
    i:nth-child(3) {
      height: 60%;
    }
    .playing i {
      animation: bounce 0.9s infinite ease-in-out;
    }
    .playing i:nth-child(2) {
      animation-delay: 0.2s;
    }
    .playing i:nth-child(3) {
      animation-delay: 0.4s;
    }
    @keyframes bounce {
      0%,
      100% {
        height: 25%;
      }
      50% {
        height: 100%;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .playing i {
        animation: none;
      }
    }
  `,
})
export class Equalizer {
  readonly playing = input(false);
}
