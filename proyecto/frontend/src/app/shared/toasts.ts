import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

@Component({
  selector: 'app-toasts',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="toast.kind" role="status">
          <app-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'warn' : 'star'" [size]="18" />
          <span>{{ toast.message }}</span>
          <button type="button" aria-label="Cerrar" (click)="toasts.dismiss(toast.id)">
            <app-icon name="close" [size]="16" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack {
      position: fixed;
      right: 16px;
      bottom: calc(var(--player-height) + 24px);
      display: flex;
      flex-direction: column;
      gap: 8px;
      z-index: 1000;
      max-width: min(420px, calc(100vw - 32px));
    }
    .toast {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 14px;
      border-radius: 6px;
      background: #2a2a2a;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
      border-left: 4px solid var(--text-muted);
    }
    .toast.error {
      border-left-color: var(--danger);
    }
    .toast.success {
      border-left-color: var(--accent);
    }
    .toast span {
      flex: 1;
    }
    button {
      color: var(--text-muted);
      line-height: 0;
    }
  `,
})
export class Toasts {
  protected readonly toasts = inject(ToastService);
}
