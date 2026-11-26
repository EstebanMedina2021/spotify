import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="sk" [style.width]="width()" [style.height]="height()" [style.border-radius]="radius()"
    aria-hidden="true"></div>`,
  styles: `
    :host {
      display: block;
    }
    .sk {
      background: linear-gradient(90deg, #232323 25%, #333 50%, #232323 75%);
      background-size: 200% 100%;
      animation: shimmer 1.4s infinite linear;
    }
    @keyframes shimmer {
      from {
        background-position: 200% 0;
      }
      to {
        background-position: -200% 0;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .sk {
        animation: none;
      }
    }
  `,
})
export class Skeleton {
  readonly width = input('100%');
  readonly height = input('16px');
  readonly radius = input('4px');
}
