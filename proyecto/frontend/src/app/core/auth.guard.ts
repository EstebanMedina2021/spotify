import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

/** Lets the route through only when GET /auth/me confirms a session; otherwise goes to /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.session()) {
    return of(true);
  }
  return auth.checkSession().pipe(map((ok) => (ok ? true : router.createUrlTree(['/login']))));
};
