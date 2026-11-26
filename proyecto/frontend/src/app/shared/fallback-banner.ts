import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon } from './icon';

/** Yellow notice shown when the backend answered with `fallback=true` (AI or Spotify degraded). */
@Component({
  selector: 'app-fallback-banner',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="banner" role="status">
      <app-icon name="warn" [size]="20" />
      <div>
        <p>{{ mensaje() }}</p>
        @if (motivo()) {
          <small>Motivo: {{ motivo() }}</small>
        }
      </div>
    </div>
  `,
  styles: `
    .banner {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      padding: 12px 16px;
      border-radius: var(--radius);
      background: rgba(245, 197, 24, 0.12);
      border: 1px solid rgba(245, 197, 24, 0.5);
      color: var(--warning);
    }
    small {
      color: var(--text-muted);
    }
  `,
})
export class FallbackBanner {
  readonly mensaje = input.required<string>();
  readonly motivo = input<string | null>(null);
}
