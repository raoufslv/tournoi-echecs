import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { errorMessage } from '../../core/error-messages';
import { GameService } from '../../core/game.service';

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
  `,
})
export class LobbyComponent {
  private readonly games = inject(GameService);
  private readonly router = inject(Router);

  protected code = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected create(): void {
    this.run(this.games.create());
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
