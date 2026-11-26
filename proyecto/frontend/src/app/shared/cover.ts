import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { coverGradient, initialOf } from '../core/util';

/** Generated cover: the API has no artwork, so the gradient is derived from a seed string. */
@Component({
  selector: 'app-cover',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="cover" [style.background]="gradient()" [style.width]="dimension()" [style.height]="dimension()"
    [class.round]="round()" aria-hidden="true">
    <span [style.font-size.px]="fontSize()">{{ initial() }}</span>
  </div>`,
  styles: `
    :host {
      display: block;
      line-height: 0;
    }
    .cover {
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
      color: rgba(255, 255, 255, 0.85);
      font-weight: 700;
      box-shadow: 0 4px 60px rgba(0, 0, 0, 0.3);
      user-select: none;
      width: 100%;
      aspect-ratio: 1;
    }
    .cover.round {
      border-radius: 50%;
    }
  `,
})
export class Cover {
  readonly seed = input.required<string>();
  /** Pixel size; when omitted the cover fills its container (square). */
  readonly size = input<number | null>(null);
  readonly round = input(false);

  protected readonly gradient = computed(() => coverGradient(this.seed()));
  protected readonly initial = computed(() => initialOf(this.seed()));
  protected readonly dimension = computed(() => (this.size() ? `${this.size()}px` : null));
  protected readonly fontSize = computed(() => (this.size() ? Math.max(12, this.size()! / 2.2) : 48));
}
