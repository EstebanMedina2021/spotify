import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { errorInterceptor } from '../core/interceptors';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  function create() {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    // ngOnInit probes the session; no cookie yet -> 401
    http.expectOne('/api/auth/me').flush({ codigo: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
    return fixture;
  }

  it('does not call the backend when the form is empty', async () => {
    const fixture = create();
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone('/api/auth/login');
    expect(el.textContent).toContain('El usuario es obligatorio');
  });

  it('logs in and navigates home', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = create();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('#username') as HTMLInputElement).value = 'demo';
    el.querySelector('#username')!.dispatchEvent(new Event('input'));
    (el.querySelector('#password') as HTMLInputElement).value = 'Demo1234!';
    el.querySelector('#password')!.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ username: 'demo', password: 'Demo1234!' });
    req.flush({ username: 'demo', expiresAt: '2030-01-01T00:00:00Z' });

    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows a clear message on wrong credentials', async () => {
    const fixture = create();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('#username') as HTMLInputElement).value = 'demo';
    el.querySelector('#username')!.dispatchEvent(new Event('input'));
    (el.querySelector('#password') as HTMLInputElement).value = 'bad';
    el.querySelector('#password')!.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/auth/login').flush({ codigo: 'INVALID_CREDENTIALS', mensaje: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.textContent).toContain('Usuario o contraseña incorrectos');
  });
});
