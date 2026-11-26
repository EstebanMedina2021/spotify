import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Titled horizontal row of cards. */
@Component({
  selector: 'app-carousel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section>
      <h2>{{ title() }}</h2>
      @if (subtitle()) {
        <p class="sub">{{ subtitle() }}</p>
      }
      <div class="row"><ng-content /></div>
    </section>
  `,
  styles: `
    section {
      margin-top: 32px;
    }
    h2 {
      font-size: 22px;
      font-weight: 700;
    }
    .sub {
      color: var(--text-muted);
      margin-top: 4px;
    }
    .row {
      display: flex;
      gap: 16px;
      margin-top: 16px;
      overflow-x: auto;
      padding-bottom: 12px;
      scroll-snap-type: x proximity;
    }
    .row > * {
      scroll-snap-align: start;
    }
  `,
})
export class Carousel {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}
