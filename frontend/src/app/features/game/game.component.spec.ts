import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Chess } from 'chess.js';
import { Subject, of, throwError } from 'rxjs';

import { Game, GameEvent, MoveEntry } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { GameService } from '../../core/game.service';
import { LiveMessage, LiveService } from '../../core/live.service';
import { BoardComponent, isPromotionMove, legalDests } from './board.component';
import { GameComponent } from './game.component';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const AFTER_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1';
const AFTER_E5 = 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2';
const AFTER_NF3 = 'rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2';

function game(overrides: Partial<Game> = {}): Game {
  return {
    code: 'ABC123',
    fen: START,
    status: 'IN_PROGRESS',
    nextMoveNumber: 1,
    whiteId: 1,
    whiteName: 'Blancs',
    blackId: 2,
    blackName: 'Noirs',
    turn: 'WHITE',
    result: null,
    endReason: null,
    ...overrides,
  };
}

function moveEvent(moveNumber: number, fenAfter: string, san = 'x'): GameEvent {
  return { type: 'move.played', code: 'ABC123', moveNumber, uci: 'x', san, fenAfter, playedBy: 'white' };
}

const TWO_MOVES: MoveEntry[] = [
  { moveNumber: 1, uci: 'e2e4', san: 'e4', fenAfter: AFTER_E4 },
  { moveNumber: 2, uci: 'e7e5', san: 'e5', fenAfter: AFTER_E5 },
];

async function render(userId: number | null, g: Game, history: MoveEntry[] = []) {
  const live = new Subject<LiveMessage>();
  const get = jasmine.createSpy('get').and.returnValue(of(g));
  const movesSpy = jasmine.createSpy('moves').and.returnValue(of(history));
  const resign = jasmine.createSpy('resign').and.returnValue(of({ ...g, status: 'FINISHED' }));
  TestBed.configureTestingModule({
    imports: [GameComponent],
    providers: [
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ code: g.code }) } } },
      { provide: GameService, useValue: { get, moves: movesSpy, resign, play: () => of(g) } },
      { provide: AuthService, useValue: { userId: () => userId } },
      { provide: LiveService, useValue: { watch: () => live.asObservable() } },
    ],
  });
  const fixture = TestBed.createComponent(GameComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const board = () =>
    fixture.debugElement.query(By.directive(BoardComponent)).componentInstance as BoardComponent;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) =>
    el().querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
  return { fixture, board, live, get, movesSpy, resign, el, button };
}

describe('GameComponent', () => {
  async function movable(userId: number | null, g: Game) {
    const { fixture, board } = await render(userId, g);
    const result = { movable: board().movableColor(), orientation: board().orientation() };
    fixture.destroy();
    return result;
  }

  it('lets white play when it is white to move', async () => {
    expect((await movable(1, game())).movable).toBe('white');
  });

  it('orients the board for black; the turn check is done by the board itself', async () => {
    const r = await movable(2, game());
    expect(r.movable).toBe('black');
    expect(r.orientation).toBe('black');
  });

  it('keeps spectators read-only', async () => {
    expect((await movable(99, game())).movable).toBeNull();
  });

  it('keeps the board read-only while waiting for an opponent', async () => {
    const waiting = game({ status: 'WAITING', blackId: null, blackName: null });
    expect((await movable(1, waiting)).movable).toBeNull();
  });

  it('applies a more recent live event to the board', async () => {
    const { fixture, board, live } = await render(99, game());

    live.next({ kind: 'event', event: moveEvent(1, AFTER_E4) });
    fixture.detectChanges();

    expect(board().fen()).toBe(AFTER_E4);
    fixture.destroy();
  });

  it('ignores an event that is older than what is displayed', async () => {
    const { fixture, board, live } = await render(99, game());
    live.next({ kind: 'event', event: moveEvent(1, AFTER_E4) });
    live.next({ kind: 'event', event: moveEvent(2, AFTER_E5) });

    // Rejeu d'un coup déjà vu : ne doit pas faire reculer la position.
    live.next({ kind: 'event', event: moveEvent(1, AFTER_E4) });
    fixture.detectChanges();

    expect(board().fen()).toBe(AFTER_E5);
    fixture.destroy();
  });

  it('reloads the game over REST each time the live stream (re)opens', async () => {
    const { fixture, live, get } = await render(99, game());
    expect(get).toHaveBeenCalledTimes(1);

    live.next({ kind: 'open' });
    live.next({ kind: 'open' });

    expect(get).toHaveBeenCalledTimes(3);
    fixture.destroy();
  });

  it('warns when the live stream is interrupted', async () => {
    const { fixture, live } = await render(99, game());

    live.next({ kind: 'error' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Direct interrompu');
    live.next({ kind: 'open' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Direct interrompu');
    fixture.destroy();
  });
});

describe('GameComponent history and replay', () => {
  const inProgress = () => game({ nextMoveNumber: 3, fen: AFTER_E5, turn: 'WHITE' });

  it('loads the move history and lists the moves in SAN', async () => {
    const { fixture, movesSpy, el } = await render(99, inProgress(), TWO_MOVES);

    expect(movesSpy).toHaveBeenCalledTimes(1);
    expect(el().querySelector('app-move-list')?.textContent).toContain('e4');
    expect(el().querySelector('app-move-list')?.textContent).toContain('e5');
    fixture.destroy();
  });

  it('appends moves received live to the list without reloading the history', async () => {
    const { fixture, live, movesSpy, el } = await render(99, inProgress(), TWO_MOVES);

    live.next({ kind: 'event', event: moveEvent(3, AFTER_NF3, 'Nf3') });
    fixture.detectChanges();

    expect(el().querySelector('app-move-list')?.textContent).toContain('Nf3');
    expect(movesSpy).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('steps back through the game and makes the board read-only', async () => {
    const { fixture, board, button } = await render(1, inProgress(), TWO_MOVES);
    expect(board().fen()).toBe(AFTER_E5);

    button('Coup précédent').click();
    fixture.detectChanges();

    expect(board().fen()).toBe(AFTER_E4);
    expect(board().movableColor()).toBeNull();
    button('Début de la partie').click();
    fixture.detectChanges();
    expect(board().fen()).toBe(START);
    fixture.destroy();
  });

  it('keeps the position being reviewed when a new move arrives, and can return to the live game', async () => {
    const { fixture, board, live, button, el } = await render(1, inProgress(), TWO_MOVES);
    button('Coup précédent').click();
    fixture.detectChanges();

    live.next({ kind: 'event', event: moveEvent(3, AFTER_NF3, 'Nf3') });
    fixture.detectChanges();

    expect(board().fen()).toBe(AFTER_E4);
    expect(el().textContent).toContain('Vous consultez un ancien coup');

    el().querySelector<HTMLButtonElement>('.reviewing button')!.click();
    fixture.detectChanges();
    expect(board().fen()).toBe(AFTER_NF3);
    expect(el().textContent).not.toContain('Vous consultez un ancien coup');
    fixture.destroy();
  });

  it('jumps to the position after a move chosen in the list', async () => {
    const { fixture, board, el } = await render(99, inProgress(), TWO_MOVES);

    el().querySelector<HTMLButtonElement>('app-move-list button.mv')!.click();
    fixture.detectChanges();

    expect(board().fen()).toBe(AFTER_E4);
    fixture.destroy();
  });

  it('follows the arrow keys', async () => {
    const { fixture, board } = await render(99, inProgress(), TWO_MOVES);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(board().fen()).toBe(AFTER_E4);

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(board().fen()).toBe(AFTER_E5);
    fixture.destroy();
  });
});

describe('GameComponent resignation and result', () => {
  afterEach(() => (window.confirm as jasmine.Spy | undefined)?.and?.callThrough?.());

  it('lets a player resign after confirmation', async () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const { fixture, resign, el } = await render(1, game());

    el().querySelector<HTMLButtonElement>('button.resign')!.click();

    expect(resign).toHaveBeenCalledWith('ABC123');
    fixture.destroy();
  });

  it('does nothing when the confirmation is refused', async () => {
    spyOn(window, 'confirm').and.returnValue(false);
    const { fixture, resign, el } = await render(1, game());

    el().querySelector<HTMLButtonElement>('button.resign')!.click();

    expect(resign).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('hides the resign button for spectators, waiting games and finished games', async () => {
    const spectator = await render(99, game());
    expect(spectator.el().querySelector('button.resign')).toBeNull();
    spectator.fixture.destroy();
    TestBed.resetTestingModule();

    const waiting = await render(1, game({ status: 'WAITING', blackId: null, blackName: null }));
    expect(waiting.el().querySelector('button.resign')).toBeNull();
    waiting.fixture.destroy();
    TestBed.resetTestingModule();

    const finished = await render(1, game({ status: 'FINISHED', result: '1-0', endReason: 'CHECKMATE' }));
    expect(finished.el().querySelector('button.resign')).toBeNull();
    finished.fixture.destroy();
  });

  it('shows the result banner of a finished game, to players and spectators alike', async () => {
    const finished = game({ status: 'FINISHED', result: '0-1', endReason: 'RESIGNATION' });
    const { fixture, el } = await render(99, finished);

    expect(el().querySelector('.result-banner')?.textContent).toContain('Les Noirs gagnent par abandon');
    fixture.destroy();
  });

  it('shows the banner as soon as the game.ended event arrives', async () => {
    const { fixture, live, el } = await render(99, game());
    expect(el().querySelector('.result-banner')).toBeNull();

    live.next({
      kind: 'event',
      event: { type: 'game.ended', code: 'ABC123', moveNumber: 4, result: '1-0', endReason: 'CHECKMATE', finalFen: START },
    });
    fixture.detectChanges();

    expect(el().querySelector('.result-banner')?.textContent).toContain('Les Blancs gagnent par mat');
    fixture.destroy();
  });

  it('shows the error when the resignation fails', async () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const { fixture, resign, el } = await render(1, game());
    resign.and.returnValue(throwError(() => ({ status: 409, error: { code: 'GAME_ALREADY_FINISHED' } })));

    el().querySelector<HTMLButtonElement>('button.resign')!.click();
    fixture.detectChanges();

    expect(el().querySelector('.error')?.textContent).toContain('déjà terminée');
    fixture.destroy();
  });
});

describe('board helpers', () => {
  it('computes legal destinations from the position', () => {
    const dests = legalDests(new Chess(START));
    expect(dests.get('e2')).toEqual(['e3', 'e4']);
    expect(dests.has('e7')).toBeFalse();
  });

  it('detects pawn promotion only', () => {
    const fen = '8/P7/8/8/8/8/8/k6K w - - 0 1';
    expect(isPromotionMove(fen, 'a7', 'a8')).toBeTrue();
    expect(isPromotionMove(START, 'e2', 'e4')).toBeFalse();
  });
});
