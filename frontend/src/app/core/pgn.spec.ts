import { toPgn } from './pgn';
import { MoveEntry } from './api.models';

describe('toPgn', () => {
  const moves: MoveEntry[] = [
    { moveNumber: 1, uci: 'e2e4', san: 'e4', fenAfter: 'f1' },
    { moveNumber: 2, uci: 'e7e5', san: 'e5', fenAfter: 'f2' },
    { moveNumber: 3, uci: 'g1f3', san: null, fenAfter: 'f3' },
  ];

  it('writes headers, SAN moves and a fallback to UCI', () => {
    const text = toPgn(
      { code: 'NEKHRF', whiteName: 'Alice', blackName: 'Bob', result: '0-1' },
      moves,
    );
    expect(text).toContain('[Event "Partie NEKHRF"]');
    expect(text).toContain('[White "Alice"]');
    expect(text).toContain('[Black "Bob"]');
    expect(text).toContain('[Result "0-1"]');
    expect(text).toContain('1. e4 e5 2. g1f3 0-1');
  });

  it('uses a star when the game is not finished', () => {
    const text = toPgn({ code: 'ABC', whiteName: null, blackName: null, result: null }, []);
    expect(text).toContain('[White "?"]');
    expect(text).toContain('[Result "*"]');
  });
});
