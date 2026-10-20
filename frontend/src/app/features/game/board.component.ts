import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { Chess } from 'chess.js';
import { Chessground } from '@lichess-org/chessground';
import type { Api } from '@lichess-org/chessground/api';
import type { Key } from '@lichess-org/chessground/types';

export type Side = 'white' | 'black';

export interface BoardMove {
  orig: string;
  dest: string;
  /** Vrai si le coup est une promotion de pion (le choix de la pièce reste à faire). */
  promotion: boolean;
}

/** Cases d'arrivée légales pour chaque case de départ, calculées par chess.js (affichage seulement). */
export function legalDests(chess: Chess): Map<Key, Key[]> {
  const dests = new Map<Key, Key[]>();
  for (const move of chess.moves({ verbose: true })) {
    const list = dests.get(move.from as Key) ?? [];
    list.push(move.to as Key);
    dests.set(move.from as Key, list);
  }
  return dests;
}

/** Vrai si le déplacement orig -> dest est une promotion de pion dans la position donnée. */
export function isPromotionMove(fen: string, orig: string, dest: string): boolean {
  const chess = new Chess(fen);
  const piece = chess.get(orig as never);
  return !!piece && piece.type === 'p' && (dest.endsWith('8') || dest.endsWith('1'));
}

@Component({
  selector: 'app-board',
  template: `<div #host class="board"></div>`,
  styles: `
    :host {
      display: block;
      width: 100%;
      max-width: 560px;
      aspect-ratio: 1;
      margin: 0 auto;
    }
    .board {
      width: 100%;
      height: 100%;
    }
  `,
})
export class BoardComponent implements AfterViewInit, OnDestroy {
  readonly fen = input.required<string>();
  readonly orientation = input<Side>('white');
  /** Couleur que l'utilisateur peut jouer maintenant ; null = lecture seule. */
  readonly movableColor = input<Side | null>(null);
  readonly moved = output<BoardMove>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private cg?: Api;

  constructor() {
    effect(() => {
      this.fen();
      this.orientation();
      this.movableColor();
      this.sync();
    });
  }

  ngAfterViewInit(): void {
    this.cg = Chessground(this.host().nativeElement, {
      movable: {
        free: false,
        events: {
          after: (orig, dest) =>
            this.moved.emit({
              orig,
              dest,
              promotion: isPromotionMove(this.fen(), orig, dest),
            }),
        },
      },
      premovable: { enabled: false },
      draggable: { showGhost: true },
    });
    this.sync();
  }

  ngOnDestroy(): void {
    this.cg?.destroy();
  }

  /** Remet l'échiquier dans l'état de la position serveur (coup refusé ou annulé). */
  reset(): void {
    this.sync();
  }

  private sync(): void {
    if (!this.cg) {
      return;
    }
    const fen = this.fen();
    const chess = new Chess(fen);
    const turn: Side = chess.turn() === 'w' ? 'white' : 'black';
    const mine = this.movableColor();
    const canMove = mine !== null && mine === turn;

    this.cg.set({
      fen,
      orientation: this.orientation(),
      turnColor: turn,
      check: chess.inCheck(),
      viewOnly: mine === null,
      movable: {
        color: canMove ? mine : undefined,
        dests: canMove ? legalDests(chess) : new Map(),
      },
    });
  }
}
