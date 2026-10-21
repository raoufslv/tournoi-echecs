import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, of, switchMap } from 'rxjs';

import { Tournament, TournamentPairing } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/error-messages';
import { LiveService } from '../../core/live.service';
import {
  formatScore,
  isPlayerOf,
  isRegistered,
  pairingResult,
  winners,
} from '../../core/tournament-state';
import { TournamentService } from '../../core/tournament.service';

const MIN_PLAYERS = 4;

@Component({
  selector: 'app-tournament',
  imports: [RouterLink],
  template: `
    @if (tournament(); as t) {
      <section class="card">
        <div class="head">
          <div>
            <h1>{{ t.name }}</h1>
            <p class="status">{{ statusText() }}</p>
          </div>
          <button type="button" class="secondary" (click)="copyCode(t.code)">
            {{ copied() ? 'Copié !' : 'Copier le code' }}
          </button>
        </div>

        @if (winnerText(); as text) {
          <p class="result-banner" role="status">{{ text }}</p>
        }

        @if (t.status === 'REGISTRATION') {
          <p class="hint">Organisé par {{ t.organizerName }}. Partagez le code {{ t.code }} pour inviter vos amis.</p>
          @if (!registered()) {
            <button type="button" (click)="join()" [disabled]="busy()">Rejoindre le tournoi</button>
          }
          @if (isOrganizer()) {
            <button type="button" class="start" (click)="start()" [disabled]="busy() || !canStart()">
              Lancer le tournoi
            </button>
            @if (!canStart()) {
              <p class="hint">Il faut au moins {{ minPlayers }} joueurs pour commencer.</p>
            }
          }
        }

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }

        @if (!liveConnected()) {
          <p class="live-warning" role="status">Direct interrompu, reconnexion en cours…</p>
        }

        <h2>{{ t.status === 'REGISTRATION' ? 'Inscrits' : 'Classement' }}</h2>
        <table class="standings">
          <thead>
            <tr>
              <th>#</th>
              <th>Joueur</th>
              <th>Points</th>
              @if (t.status !== 'REGISTRATION') {
                <th title="Somme des points des adversaires affrontés">Buchholz</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (s of t.standings; track s.userId) {
              <tr [class.me]="s.userId === auth.userId()">
                <td>{{ t.status === 'REGISTRATION' ? '' : s.rank }}</td>
                <td>{{ s.name }}</td>
                <td>{{ format(s.score) }}</td>
                @if (t.status !== 'REGISTRATION') {
                  <td>{{ format(s.buchholz) }}</td>
                }
              </tr>
            }
          </tbody>
        </table>

        @for (round of rounds(); track round.round) {
          <h2 class="round-title">Ronde {{ round.round }} / {{ t.roundsTotal }}</h2>
          <ul class="pairings">
            @for (g of round.games; track g.gameCode) {
              <li>
                <span class="players">{{ g.whiteName }} <small>(B)</small> – {{ g.blackName }} <small>(N)</small></span>
                <span class="result">{{ result(g) }}</span>
                @if (isMine(g) && g.status === 'IN_PROGRESS') {
                  <a class="button" [routerLink]="['/game', g.gameCode]">Jouer ma partie</a>
                } @else {
                  <a class="button secondary" [routerLink]="['/game', g.gameCode]">
                    {{ g.status === 'FINISHED' ? 'Revoir' : 'Suivre' }}
                  </a>
                }
              </li>
            }
            @if (round.byeName) {
              <li class="bye">
                <span class="players">{{ round.byeName }}</span>
                <span class="result">exempt (1 point)</span>
              </li>
            }
          </ul>
        }
      </section>
    } @else if (loadError()) {
      <p class="error" role="alert">{{ loadError() }}</p>
    } @else {
      <p>Chargement…</p>
    }
  `,
  styles: `
    .head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    .head h1 {
      margin-bottom: 0.25rem;
    }
    .status,
    .hint {
      margin: 0 0 1rem;
      color: var(--muted);
    }
    .start {
      margin-left: 0.5rem;
    }
    .result-banner {
      margin: 0 0 1rem;
      padding: 0.6rem 0.9rem;
      border-radius: 6px;
      background: #eaf0f8;
      color: var(--accent-dark);
      font-weight: 600;
    }
    .live-warning {
      font-size: 0.85rem;
      color: var(--danger);
    }
    .standings {
      width: 100%;
      border-collapse: collapse;
    }
    .standings th,
    .standings td {
      text-align: left;
      padding: 0.35rem 0.5rem;
      border-bottom: 1px solid #e5e7eb;
    }
    .standings .me {
      font-weight: 600;
    }
    .round-title {
      margin: 1.5rem 0 0.5rem;
      font-size: 1rem;
    }
    .pairings {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .pairings li {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.4rem 0;
      border-bottom: 1px solid #e5e7eb;
    }
    .pairings .players {
      flex: 1;
    }
    .pairings .result {
      color: var(--muted);
      min-width: 5rem;
      text-align: center;
    }
    .pairings .bye {
      color: var(--muted);
    }
  `,
})
export class TournamentComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly tournaments = inject(TournamentService);
  private readonly live = inject(LiveService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  /** Chaque signal de rechargement annule la requête précédente : seule la dernière réponse compte. */
  private readonly reload$ = new Subject<void>();
  private code = '';

  protected readonly minPlayers = MIN_PLAYERS;
  protected readonly tournament = signal<Tournament | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly busy = signal(false);
  protected readonly liveConnected = signal(true);

  protected readonly registered = computed(() => {
    const t = this.tournament();
    return t !== null && isRegistered(t, this.auth.userId());
  });
  protected readonly isOrganizer = computed(
    () => this.tournament()?.organizerId === this.auth.userId(),
  );
  protected readonly canStart = computed(
    () => (this.tournament()?.standings.length ?? 0) >= MIN_PLAYERS,
  );
  /** Rondes affichées de la plus récente à la plus ancienne. */
  protected readonly rounds = computed(() => [...(this.tournament()?.rounds ?? [])].reverse());

  protected readonly statusText = computed(() => {
    const t = this.tournament();
    if (!t) {
      return '';
    }
    switch (t.status) {
      case 'REGISTRATION':
        return `Inscriptions ouvertes : ${t.standings.length} joueur(s).`;
      case 'IN_PROGRESS':
        return `Ronde ${t.currentRound} sur ${t.roundsTotal}.`;
      default:
        return 'Tournoi terminé.';
    }
  });

  protected readonly winnerText = computed(() => {
    const t = this.tournament();
    const first = t ? winners(t) : [];
    if (first.length === 0) {
      return null;
    }
    const names = first.map((s) => s.name).join(', ');
    return first.length === 1 ? `Vainqueur : ${names}` : `Vainqueurs ex æquo : ${names}`;
  });

  ngOnInit(): void {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    this.reload$
      .pipe(
        switchMap(() =>
          this.tournaments.get(this.code).pipe(
            catchError((err) => {
              if (!this.tournament()) {
                this.loadError.set(errorMessage(err));
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((tournament) => {
        if (tournament) {
          this.loadError.set(null);
          this.tournament.set(tournament);
        }
      });

    this.reload$.next();
    this.live
      .watchTournament(this.code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => {
        if (message.kind === 'error') {
          this.liveConnected.set(false);
          return;
        }
        this.liveConnected.set(true);
        // Ouverture ou changement : l'état du serveur fait foi, on le recharge.
        this.reload$.next();
      });
  }

  protected join(): void {
    this.run(this.tournaments.join(this.code));
  }

  protected start(): void {
    this.run(this.tournaments.start(this.code));
  }

  protected format(score: number): string {
    return formatScore(score);
  }

  protected result(pairing: TournamentPairing): string {
    return pairingResult(pairing);
  }

  protected isMine(pairing: TournamentPairing): boolean {
    return isPlayerOf(pairing, this.auth.userId());
  }

  protected copyCode(code: string): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  private run(action: ReturnType<TournamentService['get']>): void {
    this.busy.set(true);
    this.error.set(null);
    action.subscribe({
      next: (tournament) => {
        this.tournament.set(tournament);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.busy.set(false);
      },
    });
  }
}
