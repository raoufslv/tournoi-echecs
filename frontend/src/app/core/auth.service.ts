import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';

import { TokenResponse } from './api.models';
import { API_URL } from './config';

const TOKEN_KEY = 'chess.token';

/** Lit le claim `sub` (id utilisateur) d'un JWT, ou null si le token est illisible. */
export function userIdFromToken(token: string | null): number | null {
  if (!token) {
    return null;
  }
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(payload));
    const id = Number(claims.sub);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly token = this.tokenSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);
  readonly userId = computed(() => userIdFromToken(this.tokenSignal()));

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_URL}/auth/login`, { email, password }).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.token);
        this.tokenSignal.set(res.token);
      }),
    );
  }

  /** L'API d'inscription ne renvoie pas de token : on enchaîne avec une connexion. */
  register(email: string, password: string, displayName: string): Observable<TokenResponse> {
    return this.http
      .post(`${API_URL}/auth/register`, { email, password, displayName })
      .pipe(switchMap(() => this.login(email, password)));
  }

  logout(): void {
    this.clearSession();
    this.router.navigateByUrl('/login');
  }

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
  }
}
