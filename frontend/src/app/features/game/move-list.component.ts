import { Component, computed, input, output } from '@angular/core';

import { MoveEntry } from '../../core/api.models';

interface Row {
  number: number;
  white: MoveEntry;
  black: MoveEntry | null;
}

@Component({
  selector: 'app-move-list',
  template: `
    @if (rows().length === 0) {
      <p class="empty">Aucun coup pour l'instant.</p>
    } @else {
      <ol class="rows">
        @for (row of rows(); track row.number) {
          <li>
            <span class="num">{{ row.number }}.</span>
            <button type="button" class="mv" [class.current]="current() === row.white.moveNumber"
              (click)="selected.emit(row.white.moveNumber)">{{ label(row.white) }}</button>
            @if (row.black; as black) {
              <button type="button" class="mv" [class.current]="current() === black.moveNumber"
                (click)="selected.emit(black.moveNumber)">{{ label(black) }}</button>
            }
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .rows {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
      gap: 0.15rem 0.75rem;
    }
    li {
      display: flex;
      align-items: center;
      gap: 0.25rem;
    }
    .num {
      width: 2rem;
      color: var(--muted);
      font-size: 0.85rem;
      text-align: right;
    }
    .mv {
      min-width: 3.5rem;
      padding: 0.15rem 0.4rem;
      border: 1px solid transparent;
      background: transparent;
      color: var(--text);
      text-align: left;
      font-weight: 500;
    }
    .mv:hover:not(:disabled) {
      background: #eaf0f8;
    }
    .mv.current {
      background: var(--accent);
      color: #fff;
    }
    .empty {
      margin: 0;
      color: var(--muted);
      font-size: 0.9rem;
    }
  `,
})
export class MoveListComponent {
  readonly moves = input.required<MoveEntry[]>();
  /** Numéro du dernier coup affiché sur l'échiquier (0 = position de départ). */
  readonly current = input<number>(0);
  /** Émet le numéro du coup choisi : l'échiquier doit afficher la position après ce coup. */
  readonly selected = output<number>();

  protected readonly rows = computed<Row[]>(() => {
    const moves = this.moves();
    const rows: Row[] = [];
    for (let i = 0; i < moves.length; i += 2) {
      rows.push({ number: i / 2 + 1, white: moves[i], black: moves[i + 1] ?? null });
    }
    return rows;
  });

  protected label(move: MoveEntry): string {
    return move.san ?? move.uci;
  }
}
