import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { credentialsInterceptor, errorInterceptor } from './interceptors';
import { ApiError } from './models';
import { ToastService } from './toast.service';

describe('interceptors', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, credentialsInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  function failure(url: string): Promise<ApiError> {
    return new Promise((resolve) => http.get(url).subscribe({ error: (e: ApiError) => resolve(e) }));
  }

  it('sends credentials on every request', () => {
    http.get('/api/lists').subscribe();
    const req = controller.expectOne('/api/lists');
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
  });

  it('maps the backend ErrorResponse to ApiError', async () => {
    const pending = failure('/api/lists/x');
    controller
      .expectOne('/api/lists/x')
      .flush({ codigo: 'PLAYLIST_NOT_FOUND', mensaje: "La lista 'x' no existe" }, { status: 404, statusText: 'Not Found' });

    expect(await pending).toMatchObject({ status: 404, codigo: 'PLAYLIST_NOT_FOUND', mensaje: "La lista 'x' no existe" });
  });

  it('goes to /login and clears the session on 401 outside /auth', async () => {
    const auth = TestBed.inject(AuthService);
    auth.session.set({ username: 'demo', expiresAt: 'x' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const pending = failure('/api/lists');
    controller.expectOne('/api/lists').flush({ codigo: 'UNAUTHORIZED', mensaje: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await pending;

    expect(auth.session()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not redirect on 401 from /auth calls (e.g. wrong credentials)', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const pending = failure('/api/auth/login');
    controller.expectOne('/api/auth/login').flush({ codigo: 'INVALID_CREDENTIALS', mensaje: 'x' }, { status: 401, statusText: 'Unauthorized' });

    expect((await pending).codigo).toBe('INVALID_CREDENTIALS');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('reads Retry-After on 429 and shows a toast', async () => {
    const pending = failure('/api/spotify/search');
    controller
      .expectOne('/api/spotify/search')
      .flush({ codigo: 'RATE_LIMITED', mensaje: 'x' }, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '7' } });

    expect((await pending).retryAfter).toBe(7);
    expect(TestBed.inject(ToastService).toasts()[0].message).toContain('7 s');
  });

  it('reports a network failure as NETWORK_ERROR', async () => {
    const pending = failure('/api/lists');
    controller.expectOne('/api/lists').error(new ProgressEvent('error'));

    expect(await pending).toMatchObject({ status: 0, codigo: 'NETWORK_ERROR' });
  });
});
