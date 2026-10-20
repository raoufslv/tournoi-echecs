import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Game, MoveEntry } from './api.models';
import { API_URL } from './config';

@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);

  create(): Observable<Game> {
    return this.http.post<Game>(`${API_URL}/games`, {});
  }

  get(code: string): Observable<Game> {
    return this.http.get<Game>(`${API_URL}/games/${encodeURIComponent(code)}`);
  }

  join(code: string): Observable<Game> {
    return this.http.post<Game>(`${API_URL}/games/${encodeURIComponent(code)}/join`, {});
  }

  moves(code: string): Observable<MoveEntry[]> {
    return this.http.get<MoveEntry[]>(`${API_URL}/games/${encodeURIComponent(code)}/moves`);
  }

  resign(code: string): Observable<Game> {
    return this.http.post<Game>(`${API_URL}/games/${encodeURIComponent(code)}/resign`, {});
  }

  play(code: string, uci: string): Observable<Game> {
    return this.http.post<Game>(`${API_URL}/games/${encodeURIComponent(code)}/moves`, { uci });
  }
}
