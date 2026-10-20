import { Game, GameEvent, MoveEntry, MovePlayedEvent } from './api.models';
import { START_FEN, appendMove, applyEvent, fenAtIndex, isNewer, resultText } from './game-state';

const START = START_FEN;
const AFTER_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1';
const AFTER_E5 = 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2';

const base: Game = {
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
};

const e4: MovePlayedEvent = {
  type: 'move.played',
  code: 'ABC123',
  moveNumber: 1,
  uci: 'e2e4',
  san: 'e4',
  fenAfter: AFTER_E4,
  playedBy: 'white',
};

const e5: MovePlayedEvent = { ...e4, moveNumber: 2, uci: 'e7e5', san: 'e5', fenAfter: AFTER_E5, playedBy: 'black' };

describe('applyEvent', () => {
  it('applies a new move and flips the turn', () => {
    const next = applyEvent(base, e4);
    expect(next.fen).toBe(AFTER_E4);
    expect(next.nextMoveNumber).toBe(2);
    expect(next.turn).toBe('BLACK');
  });

  it('is idempotent: a move already applied changes nothing', () => {
    const once = applyEvent(base, e4);
    expect(applyEvent(once, e4)).toBe(once);
  });

  it('seats the second player and starts the game', () => {
    const waiting: Game = { ...base, status: 'WAITING', blackId: null, blackName: null };
    const next = applyEvent(waiting, {
      type: 'player.joined',
      code: 'ABC123',
      color: 'black',
      userId: 2,
      displayName: 'Noirs',
    });
    expect(next.blackName).toBe('Noirs');
    expect(next.status).toBe('IN_PROGRESS');
  });

  it('marks the game finished with its result and reason', () => {
    const ended: GameEvent = {
      type: 'game.ended',
      code: 'ABC123',
      moveNumber: 4,
      result: '0-1',
      endReason: 'RESIGNATION',
      finalFen: START,
    };
    const next = applyEvent(base, ended);
    expect(next.status).toBe('FINISHED');
    expect(next.result).toBe('0-1');
    expect(next.endReason).toBe('RESIGNATION');
    expect(applyEvent(next, ended)).toBe(next);
  });
});

describe('isNewer', () => {
  it('accepts anything when nothing is displayed yet', () => {
    expect(isNewer(null, base)).toBeTrue();
  });

  it('rejects a state with fewer moves', () => {
    expect(isNewer({ ...base, nextMoveNumber: 3 }, { ...base, nextMoveNumber: 2 })).toBeFalse();
  });

  it('accepts a state with more moves', () => {
    expect(isNewer(base, { ...base, nextMoveNumber: 2 })).toBeTrue();
  });

  it('accepts a status or result change at the same move number, rejects an identical state', () => {
    expect(isNewer(base, { ...base, status: 'FINISHED' })).toBeTrue();
    const finished: Game = { ...base, status: 'FINISHED' };
    expect(isNewer(finished, { ...finished, result: '1-0' })).toBeTrue();
    expect(isNewer(base, { ...base })).toBeFalse();
  });
});

describe('appendMove', () => {
  const m1: MoveEntry = { moveNumber: 1, uci: 'e2e4', san: 'e4', fenAfter: AFTER_E4 };

  it('appends the next move with its SAN', () => {
    expect(appendMove([], e4)).toEqual([m1]);
  });

  it('returns the same list for a move already known', () => {
    const list = [m1];
    expect(appendMove(list, e4)).toBe(list);
  });

  it('returns null when moves are missing so the caller reloads', () => {
    expect(appendMove([], e5)).toBeNull();
  });

  it('keeps a null SAN when the event has none', () => {
    const { san: _san, ...withoutSan } = e4;
    expect(appendMove([], withoutSan as MovePlayedEvent)![0].san).toBeNull();
  });
});

describe('fenAtIndex', () => {
  const moves: MoveEntry[] = [
    { moveNumber: 1, uci: 'e2e4', san: 'e4', fenAfter: AFTER_E4 },
    { moveNumber: 2, uci: 'e7e5', san: 'e5', fenAfter: AFTER_E5 },
  ];

  it('returns the start position for index 0', () => {
    expect(fenAtIndex(moves, 0)).toBe(START);
  });

  it('returns the position after the given number of moves', () => {
    expect(fenAtIndex(moves, 1)).toBe(AFTER_E4);
    expect(fenAtIndex(moves, 2)).toBe(AFTER_E5);
  });

  it('never goes past the last known move', () => {
    expect(fenAtIndex(moves, 9)).toBe(AFTER_E5);
  });
});

describe('resultText', () => {
  it('describes wins by reason', () => {
    expect(resultText('1-0', 'CHECKMATE')).toBe('Les Blancs gagnent par mat');
    expect(resultText('0-1', 'RESIGNATION')).toBe('Les Noirs gagnent par abandon');
  });

  it('describes a draw', () => {
    expect(resultText('1/2-1/2', 'STALEMATE')).toBe('Partie nulle par pat');
  });

  it('returns null when the result is unknown', () => {
    expect(resultText(null, null)).toBeNull();
  });
});
