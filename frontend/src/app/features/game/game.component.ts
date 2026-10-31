import {
  Component,
  DestroyRef,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Game, MoveEntry } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { copyText } from '../../core/copy-text';
import { errorMessage } from '../../core/error-messages';
import { OPPONENT_MOVED_TITLE, PAGE_TITLE, shouldNotifyOpponentMove } from '../../core/notify';
import { downloadPgn, toPgn } from '../../core/pgn';
import { GameService } from '../../core/game.service';
import { appendMove, applyEvent, fenAtIndex, isNewer, resultText } from '../../core/game-state';
import { LiveService } from '../../core/live.service';
import { BoardComponent, BoardMove, Side } from './board.component';
import { MoveListComponent } from './move-list.component';

const PROMOTIONS = [
  { code: 'q', label: 'Dame' },
  { code: 'r', label: 'Tour' },
  { code: 'b', label: 'Fou' },
  { code: 'n', label: 'Cavalier' },
];

@Component({
  selector: 'app-game',
  imports: [BoardComponent, MoveListComponent, RouterLink],
  template: `
    @if (game(); as g) {
      <section class="card">
        <div class="head">
          <div>
            <h1>Partie {{ g.code }}</h1>
            <p class="status">{{ statusText() }}</p>
          </div>
          <div class="actions">
            <button type="button" class="secondary" (click)="copyCode(g.code)">
              {{ copied() ? 'Copié !' : 'Copier le code' }}
            </button>
            <button type="button" class="secondary" (click)="download()">Télécharger le PGN</button>
          </div>
        </div>

        @if (g.tournamentCode) {
          <p class="tournament-link">
            <a [routerLink]="['/tournament', g.tournamentCode]">← Retour au tournoi</a>
            @if (g.round) {
              <span> · ronde {{ g.round }}</span>
            }
          </p>
        }

        <div class="players">
          <span [class.active]="g.status === 'IN_PROGRESS' && g.turn === 'WHITE'">
            @if (clock(g, 'WHITE'); as time) {
              <strong class="clock">{{ time }}</strong>
            }
            Blancs : {{ g.whiteName ?? 'en attente…' }}
          </span>
          <span [class.active]="g.status === 'IN_PROGRESS' && g.turn === 'BLACK'">
            @if (clock(g, 'BLACK'); as time) {
              <strong class="clock">{{ time }}</strong>
            }
            Noirs : {{ g.blackName ?? 'en attente…' }}
          </span>
        </div>

        @if (banner(); as text) {
          <p class="result-banner" role="status">{{ text }}</p>
        }

        <app-board
          [fen]="shownFen()"
          [orientation]="orientation()"
          [movableColor]="reviewing() ? null : movableColor()"
          (moved)="onMove($event)"
        />

        <div class="replay" role="group" aria-label="Navigation dans la partie">
          <button type="button" class="secondary" (click)="goTo(0)" [disabled]="currentIndex() === 0"
            aria-label="Début de la partie">«</button>
          <button type="button" class="secondary" (click)="step(-1)" [disabled]="currentIndex() === 0"
            aria-label="Coup précédent">‹</button>
          <button type="button" class="secondary" (click)="step(1)"
            [disabled]="currentIndex() >= moves().length" aria-label="Coup suivant">›</button>
          <button type="button" class="secondary" (click)="goLive()"
            [disabled]="currentIndex() >= moves().length" aria-label="Dernier coup">»</button>
        </div>

        @if (reviewing()) {
          <p class="reviewing" role="status">
            Vous consultez un ancien coup.
            <button type="button" class="secondary" (click)="goLive()">Revenir à la partie en cours</button>
          </p>
        }

        @if (pending()) {
          <div class="promotion" role="dialog" aria-label="Choix de la promotion">
            <span>Promouvoir en :</span>
            @for (p of promotions; track p.code) {
              <button type="button" (click)="choosePromotion(p.code)">{{ p.label }}</button>
            }
            <button type="button" class="secondary" (click)="cancelPromotion()">Annuler</button>
          </div>
        }

        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }

        @if (!liveConnected()) {
          <p class="live-warning" role="status">Direct interrompu, reconnexion en cours…</p>
        }

        <h2 class="moves-title">Coups</h2>
        <app-move-list [moves]="moves()" [current]="currentIndex()" (selected)="goTo($event)" />

        @if (offeredByMe()) {
          <p class="hint">Nulle proposée à l'adversaire.</p>
        }
        @if (canOfferDraw()) {
          <button type="button" class="secondary" (click)="offerDraw()">Proposer la nulle</button>
        }
        @if (drawForMe()) {
          <button type="button" (click)="acceptDraw()">Accepter la nulle</button>
          <button type="button" class="secondary" (click)="declineDraw()">Refuser</button>
        }
        @if (canRematch()) {
          <button type="button" (click)="askRematch()">Revanche</button>
        }
        @if (canResign()) {
          <button type="button" class="secondary resign" (click)="resign()">Abandonner</button>
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
    .actions {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      align-items: flex-end;
    }
    .clock {
      display: inline-block;
      min-width: 3.2rem;
      margin-right: 0.4rem;
      font-variant-numeric: tabular-nums;
    }
    .hint {
      color: var(--muted);
    }
    .status {
      margin: 0 0 1rem;
      color: var(--muted);
    }
    .players {
      display: flex;
      justify-content: space-between;
      margin-bottom: 1rem;
      font-size: 0.95rem;
      color: var(--muted);
    }
    .players .active {
      color: var(--text);
      font-weight: 600;
    }
    .tournament-link {
      margin: 0 0 0.75rem;
      font-size: 0.9rem;
      color: var(--muted);
    }
    .promotion {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
      margin-top: 1rem;
    }
    .live-warning {
      margin: 1rem 0 0;
      font-size: 0.85rem;
      color: var(--danger);
    }
    .result-banner {
      margin: 0 0 1rem;
      padding: 0.6rem 0.9rem;
      border-radius: 6px;
      background: #eaf0f8;
      color: var(--accent-dark);
      font-weight: 600;
    }
    .replay {
      display: flex;
      justify-content: center;
      gap: 0.5rem;
      margin-top: 1rem;
    }
    .reviewing {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      flex-wrap: wrap;
      margin: 0.75rem 0 0;
      font-size: 0.9rem;
      color: var(--muted);
    }
    .moves-title {
      margin: 1.5rem 0 0.5rem;
      font-size: 1rem;
    }
    .resign {
      margin-top: 1.25rem;
      color: var(--danger);
      border-color: var(--danger);
    }
    .resign:hover:not(:disabled) {
      background: #fbeceb;
    }
  `,
})
export class GameComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly now = signal(Date.now());
  private timer = 0;
  private flagged = false;
  private readonly games = inject(GameService);
  private readonly auth = inject(AuthService);
  private readonly live = inject(LiveService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly board = viewChild(BoardComponent);
  private code = '';

  protected readonly promotions = PROMOTIONS;
  protected readonly game = signal<Game | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly pending = signal<BoardMove | null>(null);
  protected readonly liveConnected = signal(true);
  protected readonly moves = signal<MoveEntry[]>([]);

  /** Nombre de coups affichés sur l'échiquier quand on consulte l'historique ; null = partie en cours. */
  private readonly viewIndex = signal<number | null>(null);

  protected readonly reviewing = computed(() => this.viewIndex() !== null);
  protected readonly currentIndex = computed(() => this.viewIndex() ?? this.moves().length);
  protected readonly shownFen = computed(() =>
    this.viewIndex() === null
      ? (this.game()?.fen ?? '')
      : fenAtIndex(this.moves(), this.viewIndex() as number),
  );
  protected readonly banner = computed(() => {
    const g = this.game();
    return g?.status === 'FINISHED' ? resultText(g.result, g.endReason) : null;
  });
  protected readonly canResign = computed(
    () => this.game()?.status === 'IN_PROGRESS' && this.mySide() !== null,
  );

  protected readonly canOfferDraw = computed(() => {
    const g = this.game();
    return g?.status === 'IN_PROGRESS' && this.mySide() !== null && g.drawOfferedBy == null;
  });

  protected readonly offeredByMe = computed(() => {
    const g = this.game();
    return g?.status === 'IN_PROGRESS' && g.drawOfferedBy != null && g.drawOfferedBy === this.auth.userId();
  });

  protected readonly drawForMe = computed(() => {
    const g = this.game();
    const me = this.auth.userId();
    return g?.status === 'IN_PROGRESS' && g.drawOfferedBy != null && me !== null && g.drawOfferedBy !== me
      && this.mySide() !== null;
  });

  protected readonly canRematch = computed(
    () => this.game()?.status === 'FINISHED' && this.mySide() !== null,
  );

  protected clock(game: Game, color: 'WHITE' | 'BLACK'): string | null {
    const stored = color === 'WHITE' ? game.whiteRemainingMs : game.blackRemainingMs;
    if (stored == null) {
      return null;
    }
    const running = game.status === 'IN_PROGRESS' && game.turn === color && game.clockDeadline != null;
    const ms = running ? Math.max(0, (game.clockDeadline as number) - this.now()) : stored;
    const seconds = Math.ceil(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${String(rest).padStart(2, '0')}`;
  }

  protected readonly mySide = computed<Side | null>(() => {
    const g = this.game();
    const me = this.auth.userId();
    if (!g || me === null) {
      return null;
    }
    if (g.whiteId === me) {
      return 'white';
    }
    if (g.blackId === me) {
      return 'black';
    }
    return null;
  });

  protected readonly orientation = computed<Side>(() => this.mySide() ?? 'white');

  /** Un joueur ne peut jouer que si la partie est en cours ; un spectateur reste en lecture seule. */
  protected readonly movableColor = computed<Side | null>(() =>
    this.game()?.status === 'IN_PROGRESS' ? this.mySide() : null,
  );

  protected readonly statusText = computed(() => {
    const g = this.game();
    if (!g) {
      return '';
    }
    if (g.status === 'WAITING') {
      return 'En attente d’un adversaire. Partagez le code de la partie.';
    }
    if (g.status === 'FINISHED') {
      return 'Partie terminée.';
    }
    const turnLabel = g.turn === 'WHITE' ? 'aux Blancs' : 'aux Noirs';
    const mine = this.mySide();
    if (mine === null) {
      return `Vous observez la partie. C’est ${turnLabel} de jouer.`;
    }
    const myTurn = (g.turn === 'WHITE') === (mine === 'white');
    return myTurn ? 'À vous de jouer.' : 'En attente du coup de l’adversaire.';
  });

  ngOnInit(): void {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    document.addEventListener('visibilitychange', this.onVisible);
    this.timer = window.setInterval(() => {
      this.now.set(Date.now());
      const current = this.game();
      if (!this.flagged && current?.status === 'IN_PROGRESS' && current.clockDeadline != null
          && current.clockDeadline <= Date.now()) {
        this.flagged = true;
        this.refresh();
      }
    }, 200);
    this.refresh();
    this.startLive();
  }

  constructor() {
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', this.onVisible);
      window.clearInterval(this.timer);
      document.title = PAGE_TITLE;
    });
  }

  protected onMove(move: BoardMove): void {
    this.error.set(null);
    if (move.promotion) {
      this.pending.set(move);
      return;
    }
    this.send(`${move.orig}${move.dest}`);
  }

  protected choosePromotion(piece: string): void {
    const move = this.pending();
    if (!move) {
      return;
    }
    this.pending.set(null);
    this.send(`${move.orig}${move.dest}${piece}`);
  }

  protected cancelPromotion(): void {
    this.pending.set(null);
    this.board()?.reset();
  }

  /** Affiche la position après `index` coups ; au-delà du dernier coup on revient à la partie en cours. */
  protected goTo(index: number): void {
    const last = this.moves().length;
    this.viewIndex.set(index >= last ? null : Math.max(0, index));
  }

  protected step(delta: number): void {
    this.goTo(this.currentIndex() + delta);
  }

  protected goLive(): void {
    this.viewIndex.set(null);
  }

  @HostListener('window:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) {
      return;
    }
    if (event.key === 'ArrowLeft') {
      this.step(-1);
    } else if (event.key === 'ArrowRight') {
      this.step(1);
    } else if (event.key === 'Home') {
      this.goTo(0);
    } else if (event.key === 'End') {
      this.goLive();
    }
  }

  protected resign(): void {
    if (!window.confirm('Voulez-vous vraiment abandonner la partie ?')) {
      return;
    }
    this.error.set(null);
    this.games.resign(this.code).subscribe({
      next: (game) => this.accept(game),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected offerDraw(): void {
    this.games.offerDraw(this.code).subscribe({
      next: (game) => this.accept(game),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected acceptDraw(): void {
    this.games.acceptDraw(this.code).subscribe({
      next: (game) => this.accept(game),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected declineDraw(): void {
    this.games.declineDraw(this.code).subscribe({
      next: (game) => this.accept(game),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected askRematch(): void {
    this.games.rematch(this.code).subscribe({
      next: (game) => void this.router.navigate(['/game', game.code]),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected download(): void {
    const current = this.game();
    if (!current) {
      return;
    }
    downloadPgn(`partie-${current.code}.pgn`, toPgn(current, this.moves()));
  }

  private readonly onVisible = (): void => {
    if (!document.hidden) {
      document.title = PAGE_TITLE;
    }
  };

  protected copyCode(code: string): void {
    void copyText(code).then((ok) => {
      if (!ok) {
        this.error.set('Impossible de copier le code.');
        return;
      }
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  private send(uci: string): void {
    this.games.play(this.code, uci).subscribe({
      next: (game) => this.accept(game),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.board()?.reset();
      },
    });
  }

  /** Charge l'état par REST. Sert au premier affichage et à rattraper les événements manqués. */
  private refresh(): void {
    this.games.get(this.code).subscribe({
      next: (game) => {
        this.loadError.set(null);
        this.accept(game);
      },
      error: (err) => {
        if (!this.game()) {
          this.loadError.set(errorMessage(err));
        }
      },
    });
  }

  private startLive(): void {
    this.live
      .watch(this.code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => {
        switch (message.kind) {
          case 'open':
            this.liveConnected.set(true);
            // On vient de (re)connecter le flux : rattraper ce qui s'est passé entre-temps.
            this.refresh();
            break;
          case 'error':
            this.liveConnected.set(false);
            break;
          case 'event': {
            const event = message.event;
            // L'historique d'abord : accept() vérifie ensuite qu'il est à jour avec la partie.
            if (event.type === 'rematch.created') {
              void this.router.navigate(['/game', event.rematchCode]);
              break;
            }
            if (event.type === 'draw.offered' || event.type === 'draw.declined' || event.type === 'game.ended') {
              this.flagged = false;
              this.refresh();
            }
            if (event.type === 'move.played') {
              this.flagged = false;
              this.refresh();
              if (shouldNotifyOpponentMove(document.hidden, event.playedBy, this.mySide())) {
                document.title = OPPONENT_MOVED_TITLE;
                beep();
              }
              const next = appendMove(this.moves(), event);
              if (next) {
                this.moves.set(next);
              } else {
                this.loadMoves();
              }
            }
            const current = this.game();
            if (current) {
              this.accept(applyEvent(current, event));
            }
            break;
          }
        }
      });
  }

  /** Adopte un état seulement s'il est plus récent que celui affiché (ignore doublons et échos). */
  private accept(incoming: Game): void {
    if (!isNewer(this.game(), incoming)) {
      return;
    }
    // Un coup adverse change la position : un choix de promotion en cours n'a plus de sens.
    if (this.pending()) {
      this.pending.set(null);
    }
    this.game.set(incoming);
    // La partie compte plus de coups que l'historique chargé (mon propre coup, rattrapage) : recharger.
    if (this.moves().length < incoming.nextMoveNumber - 1) {
      this.loadMoves();
    }
  }

  private loadMoves(): void {
    this.games.moves(this.code).subscribe({
      next: (moves) => {
        // Ne jamais remplacer l'historique par une version plus courte (réponse arrivée en retard).
        if (moves.length >= this.moves().length) {
          this.moves.set(moves);
        }
      },
      error: () => undefined,
    });
  }
}

function beep(): void {
  const context = new AudioContext();
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.frequency.value = 880;
  gain.gain.value = 0.05;
  oscillator.connect(gain);
  gain.connect(context.destination);
  oscillator.start();
  oscillator.stop(context.currentTime + 0.12);
  oscillator.onended = () => void context.close();
}
