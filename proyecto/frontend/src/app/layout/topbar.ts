import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-topbar',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header>
      <div class="history">
        <button type="button" aria-label="Atrás" title="Atrás" (click)="location.back()">
          <app-icon name="back" />
        </button>
        <button type="button" aria-label="Adelante" title="Adelante" (click)="location.forward()">
          <app-icon name="forward" />
        </button>
      </div>

      <div class="profile">
        <button type="button" class="profile-btn" aria-haspopup="menu" [attr.aria-expanded]="isMenuOpen()"
          (click)="toggleMenu($event)">
          <span class="avatar"><app-icon name="user" [size]="18" /></span>
          <span class="name">{{ auth.username() }}</span>
          <app-icon name="chevronDown" [size]="18" />
        </button>
        @if (isMenuOpen()) {
          <div class="menu" role="menu">
            <button type="button" role="menuitem" (click)="logout()">
              <app-icon name="logout" [size]="18" /> Cerrar sesión
            </button>
          </div>
        }
      </div>
    </header>
  `,
  styles: `
    :host {
      position: sticky;
      top: 0;
      z-index: 10;
      display: block;
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 24px;
      background: rgba(18, 18, 18, 0.7);
      backdrop-filter: blur(12px);
    }
    .history {
      display: flex;
      gap: 8px;
    }
    .history button {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: rgba(0, 0, 0, 0.7);
    }
    .history button:hover {
      background: #000;
    }
    .profile {
      position: relative;
    }
    .profile-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 8px 4px 4px;
      border-radius: 500px;
      background: rgba(0, 0, 0, 0.7);
    }
    .profile-btn:hover {
      background: #282828;
    }
    .avatar {
      display: grid;
      place-items: center;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #535353;
    }
    .name {
      font-weight: 700;
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .menu {
      position: absolute;
      right: 0;
      top: calc(100% + 6px);
      min-width: 180px;
      background: #282828;
      border-radius: 6px;
      padding: 4px;
      box-shadow: 0 16px 24px rgba(0, 0, 0, 0.5);
    }
    .menu button {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 10px 12px;
      border-radius: 4px;
      text-align: left;
    }
    .menu button:hover {
      background: rgba(255, 255, 255, 0.1);
    }
    @media (max-width: 600px) {
      .name {
        display: none;
      }
    }
  `,
})
export class Topbar {
  protected readonly auth = inject(AuthService);
  protected readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly isMenuOpen = signal(false);

  protected toggleMenu(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.update((open) => !open);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: Event): void {
    if (this.isMenuOpen() && !this.host.nativeElement.contains(event.target as Node)) {
      this.isMenuOpen.set(false);
    }
  }

  protected logout(): void {
    this.isMenuOpen.set(false);
    // Whatever the server answers, the local session is dropped (finalize in AuthService).
    this.auth.logout().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }
}
