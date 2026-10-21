import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { errorMessage } from '../../core/error-messages';
import { GameService } from '../../core/game.service';
import { TournamentService } from '../../core/tournament.service';

@Component({
  selector: 'app-lobby',
  imports: [FormsModule],
  template: `
    <section class="card">
      <h1>Nouvelle partie</h1>
      <p class="hint">Créez une partie puis partagez son code avec votre adversaire.</p>
      <button type="button" (click)="create()" [disabled]="loading()">Créer une partie</button>
    </section>

    <section class="card second">
      <h2>Rejoindre une partie</h2>
      <label for="code">Code de la partie</label>
      <input
        id="code"
        type="text"
        [(ngModel)]="code"
        (keyup.enter)="join()"
        maxlength="12"
        placeholder="AK9T62"
      />
      <button type="button" class="secondary" (click)="join()" [disabled]="!code.trim() || loading()">
        Rejoindre
      </button>
    </section>

    <section class="card second">
      <h2>Tournoi entre amis</h2>
      <p class="hint">
        Format suisse, 4 à 8 joueurs : les parties sont créées automatiquement à chaque ronde.
      </p>
      <label for="tournament-name">Nom du tournoi</label>
      <input
        id="tournament-name"
        type="text"
        [(ngModel)]="tournamentName"
        maxlength="60"
        placeholder="Soirée du vendredi"
      />
      <label for="tournament-rounds">Nombre de rondes</label>
      <select id="tournament-rounds" [(ngModel)]="rounds">
        <option [ngValue]="3">3</option>
        <option [ngValue]="4">4</option>
        <option [ngValue]="5">5</option>
      </select>
      <button
        type="button"
        (click)="createTournament()"
        [disabled]="!tournamentName.trim() || loading()"
      >
        Créer un tournoi
      </button>

      <label for="tournament-code" class="open-label">Ouvrir un tournoi avec son code</label>
      <input
        id="tournament-code"
        type="text"
        [(ngModel)]="tournamentCode"
        (keyup.enter)="openTournament()"
        maxlength="12"
        placeholder="K7M3QA"
      />
      <button
        type="button"
        class="secondary"
        (click)="openTournament()"
        [disabled]="!tournamentCode.trim()"
      >
        Ouvrir
      </button>
    </section>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
  `,
  styles: `
    .second {
      margin-top: 1rem;
    }
    .hint {
      color: var(--muted);
      margin-top: 0;
    }
    select {
      width: 100%;
      padding: 0.6rem 0.75rem;
      border: 1px solid var(--border);
      border-radius: 6px;
      font: inherit;
      margin-bottom: 0.9rem;
    }
    .open-label {
      margin-top: 1.5rem;
    }
  `,
})
export class LobbyComponent {
  private readonly games = inject(GameService);
  private readonly tournaments = inject(TournamentService);
  private readonly router = inject(Router);

  protected code = '';
  protected tournamentName = '';
  protected tournamentCode = '';
  protected rounds = 3;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected create(): void {
    this.run(this.games.create());
  }

  protected createTournament(): void {
    const name = this.tournamentName.trim();
    if (!name) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.tournaments.create(name, this.rounds).subscribe({
      next: (tournament) => this.router.navigate(['/tournament', tournament.code]),
      error: (err) => this.fail(err),
    });
  }

  /** La page du tournoi propose elle-même de s'inscrire ; ici on ne fait qu'y aller. */
  protected openTournament(): void {
    const code = this.tournamentCode.trim().toUpperCase();
    if (code) {
      this.router.navigate(['/tournament', code]);
    }
  }

  protected join(): void {
    const code = this.code.trim().toUpperCase();
    if (!code) {
      return;
    }
    // Si je suis déjà joueur ou si la partie est complète, on tente d'abord de la consulter :
    // le join échoue pour le créateur, mais il peut simplement rouvrir sa partie.
    this.loading.set(true);
    this.error.set(null);
    this.games.get(code).subscribe({
      next: (game) => {
        const alreadyPlaying = game.whiteId !== null && game.blackId !== null;
        if (alreadyPlaying || game.status !== 'WAITING') {
          this.router.navigate(['/game', game.code]);
          return;
        }
        this.games.join(code).subscribe({
          next: (joined) => this.router.navigate(['/game', joined.code]),
          error: (err) => this.fail(err, code),
        });
      },
      error: (err) => this.fail(err),
    });
  }

  private run(obs: ReturnType<GameService['create']>): void {
    this.loading.set(true);
    this.error.set(null);
    obs.subscribe({
      next: (game) => this.router.navigate(['/game', game.code]),
      error: (err) => this.fail(err),
    });
  }

  private fail(err: unknown, code?: string): void {
    const apiCode = (err as { error?: { code?: string } })?.error?.code;
    // Le créateur qui "rejoint" sa propre partie reçoit GAME_FULL : on rouvre simplement la partie.
    if (code && apiCode === 'GAME_FULL') {
      this.router.navigate(['/game', code]);
      return;
    }
    this.error.set(errorMessage(err));
    this.loading.set(false);
  }
}
