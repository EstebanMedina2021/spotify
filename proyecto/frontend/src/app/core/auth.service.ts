import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';
import { API_BASE } from './api';
import { SessionInfo } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly session = signal<SessionInfo | null>(null);
  readonly username = computed(() => this.session()?.username ?? '');

  /**
   * Asks the backend who we are. The JWT lives in an HttpOnly cookie, so this is the only way to
   * know whether a session exists. It also makes the backend issue the XSRF-TOKEN cookie.
   */
  checkSession(): Observable<boolean> {
    return this.http.get<SessionInfo>(`${API_BASE}/auth/me`).pipe(
      tap((info) => this.session.set(info)),
      map(() => true),
      catchError(() => {
        this.session.set(null);
        return of(false);
      }),
    );
  }

  login(username: string, password: string): Observable<SessionInfo> {
    return this.http
      .post<SessionInfo>(`${API_BASE}/auth/login`, { username, password })
      .pipe(tap((info) => this.session.set(info)));
  }

  logout(): Observable<unknown> {
    return this.http
      .post(`${API_BASE}/auth/logout`, {})
      .pipe(finalize(() => this.clearSession()));
  }

  clearSession(): void {
    this.session.set(null);
  }
}
