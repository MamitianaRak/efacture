import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Auth } from './auth';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: Auth;

  const loginAs = (expiresAt: Date) => {
    auth.login('gerant@demo.fr', 'demo1234').subscribe();
    backend.expectOne('/api/auth/login').flush({
      token: 'jwt-123',
      expiresAt: expiresAt.toISOString(),
      user: { email: 'gerant@demo.fr', displayName: 'Gérant', role: 'MANAGER' },
    });
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(Auth);
  });

  afterEach(() => backend.verify());

  it('sends the bearer token on API calls', () => {
    loginAs(new Date(Date.now() + 3_600_000));

    http.get('/api/invoices').subscribe();

    expect(backend.expectOne('/api/invoices').request.headers.get('Authorization')).toBe('Bearer jwt-123');
    expect(auth.isManager()).toBe(true);
  });

  it('does not send an expired token', () => {
    loginAs(new Date(Date.now() - 1000));

    http.get('/api/invoices').subscribe();

    expect(backend.expectOne('/api/invoices').request.headers.has('Authorization')).toBe(false);
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('logs out and goes to the login page when the API answers 401', () => {
    loginAs(new Date(Date.now() + 3_600_000));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get('/api/invoices').subscribe({ error: () => undefined });
    backend.expectOne('/api/invoices').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], expect.anything());
  });
});
