import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Game, MyTournament } from '../../core/api.models';
import { errorMessage } from '../../core/error-messages';
import { GameService } from '../../core/game.service';
import { TournamentService } from '../../core/tournament.service';

interface Row {
  code: string;
  label: string;
  detail: string;
  link: string[];
}

const GAME_STATUS: Record<Game['status'], string> = {
  WAITING: 'en attente',
  IN_PROGRESS: 'en cours',
  FINISHED: 'terminée',
};

const TOURNAMENT_STATUS: Record<MyTournament['status'], string> = {
  REGISTRATION: 'inscriptions',
  IN_PROGRESS: 'en cours',
  FINISHED: 'terminé',
};

@Component({
  selector: 'app-mine',
  imports: [RouterLink],
  template: `
    <section class="card">
      <h1>{{ games() ? 'Mes parties' : 'Mes tournois' }}</h1>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      } @else if (!loading() && rows().length === 0) {
        <p class="hint">Rien pour l'instant.</p>
      }
      <ul class="mine">
        @for (row of rows(); track row.code) {
          <li>
            <a [routerLink]="row.link">{{ row.label }}</a>
            <span>{{ row.detail }}</span>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    .hint {
      color: var(--muted);
    }
    .mine {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .mine li {
      display: flex;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 0.35rem 1rem;
      padding: 0.85rem 0;
      border-bottom: 1px solid var(--border);
    }
    .mine span {
      color: var(--muted);
    }
  `,
})
export class MineComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly gamesApi = inject(GameService);
  private readonly tournamentsApi = inject(TournamentService);

  protected readonly games = signal(true);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<Row[]>([]);

  ngOnInit(): void {
    const kind = this.route.snapshot.data['kind'];
    this.games.set(kind !== 'tournaments');
    if (this.games()) {
      this.gamesApi.mine().subscribe({
        next: (list) => {
          this.rows.set(list.map((game) => this.gameRow(game)));
          this.loading.set(false);
        },
        error: (err) => this.fail(err),
      });
    } else {
      this.tournamentsApi.mine().subscribe({
        next: (list) => {
          this.rows.set(list.map((tournament) => this.tournamentRow(tournament)));
          this.loading.set(false);
        },
        error: (err) => this.fail(err),
      });
    }
  }

  private fail(err: unknown): void {
    this.error.set(errorMessage(err));
    this.loading.set(false);
  }

  private gameRow(game: Game): Row {
    const names = `${game.whiteName ?? '…'} – ${game.blackName ?? '…'}`;
    const detail = game.result ?? GAME_STATUS[game.status];
    return {
      code: game.code,
      label: `${game.code} · ${names}`,
      detail,
      link: ['/game', game.code],
    };
  }

  private tournamentRow(tournament: MyTournament): Row {
    const role = tournament.organizer ? 'organisateur' : 'joueur';
    return {
      code: tournament.code,
      label: tournament.name,
      detail: `${TOURNAMENT_STATUS[tournament.status]} · ${role}`,
      link: ['/tournament', tournament.code],
    };
  }
}
