import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { isApiError } from '../core/models';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="screen">
      <form class="card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="logo"><app-icon name="music" [size]="40" /></div>
        <h1>Inicia sesión</h1>

        <div class="field">
          <label for="username">Usuario</label>
          <input id="username" type="text" formControlName="username" autocomplete="username" maxlength="50"
            [class.invalid]="showError('username')" />
          @if (showError('username')) {
            <span class="error">El usuario es obligatorio.</span>
          }
        </div>

        <div class="field">
          <label for="password">Contraseña</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password"
            [class.invalid]="showError('password')" />
          @if (showError('password')) {
            <span class="error">La contraseña es obligatoria.</span>
          }
        </div>

        @if (errorMessage()) {
          <p class="error banner" role="alert">{{ errorMessage() }}</p>
        }

        <button type="submit" class="btn primary" [disabled]="isSubmitting()">
          {{ isSubmitting() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>
    </div>
  `,
  styles: `
    .screen {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 16px;
      background: linear-gradient(180deg, #1f3a2b 0%, #121212 60%);
    }
    .card {
      width: min(400px, 100%);
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 32px;
      border-radius: 12px;
      background: rgba(0, 0, 0, 0.75);
    }
    .logo {
      color: var(--accent);
      text-align: center;
    }
    h1 {
      text-align: center;
      font-size: 28px;
    }
    .banner {
      padding: 10px 12px;
      border-radius: 4px;
      background: rgba(233, 20, 41, 0.15);
      font-size: 13px;
    }
    .btn {
      margin-top: 8px;
    }
  `,
})
export class LoginPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required]],
  });
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal('');

  ngOnInit(): void {
    // Also primes the XSRF-TOKEN cookie, which the backend issues on any request.
    this.auth.checkSession().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((ok) => {
      if (ok) {
        void this.router.navigate(['/']);
      }
    });
  }

  protected showError(name: 'username' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.errorMessage.set('');
    const { username, password } = this.form.getRawValue();

    this.auth.login(username.trim(), password).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        void this.router.navigate(['/']);
      },
      error: (error: unknown) => {
        this.isSubmitting.set(false);
        if (isApiError(error) && error.codigo === 'INVALID_CREDENTIALS') {
          this.errorMessage.set('Usuario o contraseña incorrectos.');
        } else if (isApiError(error) && error.status === 429) {
          this.errorMessage.set('Demasiados intentos. Espera un momento e intenta de nuevo.');
        } else {
          this.errorMessage.set(isApiError(error) ? error.mensaje : 'No se pudo iniciar sesión.');
        }
      },
    });
  }
}
