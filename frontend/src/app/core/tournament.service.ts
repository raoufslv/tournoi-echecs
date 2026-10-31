import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MyTournament, Tournament } from './api.models';
import { API_URL } from './config';

@Injectable({ providedIn: 'root' })
export class TournamentService {
  private readonly http = inject(HttpClient);

  mine(): Observable<MyTournament[]> {
    return this.http.get<MyTournament[]>(`${API_URL}/tournaments/mine`);
  }

  forfeit(code: string, gameCode: string, color: 'white' | 'black'): Observable<unknown> {
    return this.http.post(`${API_URL}/tournaments/${encodeURIComponent(code)}/games/${encodeURIComponent(gameCode)}/forfeit`, {
      color,
    });
  }

  create(name: string, rounds: number, minutes: number): Observable<Tournament> {
    return this.http.post<Tournament>(`${API_URL}/tournaments`, { name, rounds, minutes });
  }

  get(code: string): Observable<Tournament> {
    return this.http.get<Tournament>(`${API_URL}/tournaments/${encodeURIComponent(code)}`);
  }

  join(code: string): Observable<Tournament> {
    return this.http.post<Tournament>(`${API_URL}/tournaments/${encodeURIComponent(code)}/join`, {});
  }

  start(code: string): Observable<Tournament> {
    return this.http.post<Tournament>(`${API_URL}/tournaments/${encodeURIComponent(code)}/start`, {});
  }
}
