import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { API_URL } from './config';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.setItem('chess.token', 'tok');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('adds the bearer token to API calls', () => {
    http.get(`${API_URL}/games/ABC`).subscribe();
    const req = ctrl.expectOne(`${API_URL}/games/ABC`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok');
    req.flush({});
  });

  it('does not add the token to auth calls', () => {
    http.post(`${API_URL}/auth/login`, {}).subscribe();
    const req = ctrl.expectOne(`${API_URL}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('clears the session and redirects to /login on 401', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    http.get(`${API_URL}/games/ABC`).subscribe({ error: () => undefined });
    ctrl.expectOne(`${API_URL}/games/ABC`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
