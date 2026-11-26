import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiError } from './models';
import { ToastService } from './toast.service';

/** Sends cookies on every request (the JWT travels in an HttpOnly cookie). */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ withCredentials: true }));

export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return {
        status: 0,
        codigo: 'NETWORK_ERROR',
        mensaje: 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.',
      };
    }
    const body = (error.error ?? {}) as { codigo?: string; mensaje?: string };
    const retryHeader = Number(error.headers?.get('Retry-After'));
    return {
      status: error.status,
      codigo: body.codigo ?? 'UNKNOWN_ERROR',
      mensaje: body.mensaje ?? 'Ocurrió un error inesperado.',
      retryAfter: Number.isFinite(retryHeader) && retryHeader > 0 ? retryHeader : undefined,
    };
  }
  return { status: -1, codigo: 'UNKNOWN_ERROR', mensaje: 'Ocurrió un error inesperado.' };
}

/**
 * Normalizes every failure into an ApiError (by `codigo`), redirects to /login when the session
 * is gone, and shows a toast for errors the page cannot sensibly handle inline.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const apiError = toApiError(error);
      const isAuthCall = req.url.includes('/auth/');

      if (apiError.status === 401 && !isAuthCall) {
        auth.clearSession();
        toast.error('Tu sesión expiró. Inicia sesión de nuevo.');
        void router.navigate(['/login']);
      } else if (apiError.codigo === 'CSRF_INVALID') {
        toast.error('Token de seguridad inválido. Recarga la página e intenta de nuevo.');
      } else if (apiError.status === 429) {
        const wait = apiError.retryAfter ? ` Reintenta en ${apiError.retryAfter} s.` : '';
        toast.error(`Demasiadas solicitudes.${wait}`);
      } else if (apiError.status === 0 || apiError.status >= 500) {
        toast.error(apiError.mensaje);
      }
      return throwError(() => apiError);
    }),
  );
};
