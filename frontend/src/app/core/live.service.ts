import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { GameEvent } from './api.models';
import { LIVE_URL } from './config';

export type LiveMessage =
  | { kind: 'open' }
  | { kind: 'error' }
  | { kind: 'event'; event: GameEvent };

export const EVENT_TYPES: GameEvent['type'][] = ['move.played', 'game.ended', 'player.joined'];

/** Permet de remplacer EventSource dans les tests. */
export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSource>(
  'EVENT_SOURCE_FACTORY',
  { providedIn: 'root', factory: () => (url: string) => new EventSource(url) },
);

/** Le tournoi n'envoie qu'un signal « quelque chose a changé » : le front recharge alors par REST. */
export type TournamentLiveMessage = { kind: 'open' } | { kind: 'error' } | { kind: 'updated' };

@Injectable({ providedIn: 'root' })
export class LiveService {
  private readonly createSource = inject(EVENT_SOURCE_FACTORY);

  /**
   * Flux du direct pour une partie. Émet `open` à chaque (re)connexion : le consommateur
   * recharge alors l'état par REST pour rattraper ce qu'il a pu manquer.
   * Le navigateur se reconnecte tout seul après une coupure.
   */
  watch(code: string): Observable<LiveMessage> {
    return new Observable<LiveMessage>((subscriber) => {
      const source = this.createSource(`${LIVE_URL}/games/${encodeURIComponent(code)}/watch`);

      source.onopen = () => subscriber.next({ kind: 'open' });
      source.onerror = () => subscriber.next({ kind: 'error' });

      for (const type of EVENT_TYPES) {
        source.addEventListener(type, (message) => {
          try {
            const event = JSON.parse((message as MessageEvent<string>).data) as GameEvent;
            subscriber.next({ kind: 'event', event });
          } catch {
            // Message illisible : on l'ignore, le prochain GET de rattrapage corrigera l'état.
          }
        });
      }

      return () => source.close();
    });
  }

  /** Flux d'un tournoi : mêmes règles que pour une partie (réouverture = rechargement REST). */
  watchTournament(code: string): Observable<TournamentLiveMessage> {
    return new Observable<TournamentLiveMessage>((subscriber) => {
      const source = this.createSource(`${LIVE_URL}/tournaments/${encodeURIComponent(code)}/watch`);

      source.onopen = () => subscriber.next({ kind: 'open' });
      source.onerror = () => subscriber.next({ kind: 'error' });
      source.addEventListener('tournament.updated', () => subscriber.next({ kind: 'updated' }));

      return () => source.close();
    });
  }
}
