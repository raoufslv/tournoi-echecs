import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService, userIdFromToken } from './auth.service';
import { API_URL } from './config';

function fakeJwt(sub: string): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64({ sub })}.signature`;
}

describe('AuthService', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  it('stores the token and exposes the user id after login', () => {
    auth.login('a@b.fr', 'password123').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush({ token: fakeJwt('42') });

    expect(auth.isLoggedIn()).toBeTrue();
    expect(auth.userId()).toBe(42);
    expect(localStorage.getItem('chess.token')).toContain('.');
  });

  it('clears the session on logout', () => {
    auth.login('a@b.fr', 'password123').subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush({ token: fakeJwt('1') });

    auth.clearSession();

    expect(auth.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem('chess.token')).toBeNull();
  });

  it('registers then logs in', () => {
    auth.register('a@b.fr', 'password123', 'Alice').subscribe();
    http.expectOne(`${API_URL}/auth/register`).flush({ id: 1 });
    http.expectOne(`${API_URL}/auth/login`).flush({ token: fakeJwt('1') });

    expect(auth.isLoggedIn()).toBeTrue();
  });

  it('returns null for an unreadable token', () => {
    expect(userIdFromToken('garbage')).toBeNull();
    expect(userIdFromToken(null)).toBeNull();
  });
});
