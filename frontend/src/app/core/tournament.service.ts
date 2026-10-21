import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Tournament } from './api.models';
import { API_URL } from './config';

@Injectable({ providedIn: 'root' })
export class TournamentService {
  private readonly http = inject(HttpClient);

  create(name: string, rounds: number): Observable<Tournament> {
    return this.http.post<Tournament>(`${API_URL}/tournaments`, { name, rounds });
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
