import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function run(): Observable<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    if (!isObservable(result)) {
      throw new Error('guard should return an observable');
    }
    return result as Observable<boolean | UrlTree>;
  }

  it('lets the user in when /auth/me answers 200', async () => {
    const pending = firstValueFrom(run());
    http.expectOne('/api/auth/me').flush({ username: 'demo', expiresAt: '2030-01-01T00:00:00Z' });

    expect(await pending).toBe(true);
    expect(TestBed.inject(AuthService).username()).toBe('demo');
  });

  it('redirects to /login when /auth/me answers 401', async () => {
    const pending = firstValueFrom(run());
    http.expectOne('/api/auth/me').flush({ codigo: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    const result = await pending;
    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('does not call the backend again when a session is already known', async () => {
    TestBed.inject(AuthService).session.set({ username: 'demo', expiresAt: '2030-01-01T00:00:00Z' });

    expect(await firstValueFrom(run())).toBe(true);
    http.expectNone('/api/auth/me');
  });
});
