import { Standing, Tournament, TournamentPairing } from './api.models';
import { formatScore, isPlayerOf, isRegistered, pairingResult, winners } from './tournament-state';

function standing(userId: number, name: string, score: number, rank: number): Standing {
  return { rank, userId, name, score, buchholz: 0, hadBye: false };
}

function tournament(overrides: Partial<Tournament> = {}): Tournament {
  return {
    code: 'TOURN1',
    name: 'Soirée',
    status: 'IN_PROGRESS',
    roundsTotal: 3,
    currentRound: 1,
    organizerId: 1,
    organizerName: 'Alice',
    standings: [standing(1, 'Alice', 1, 1), standing(2, 'Bob', 0.5, 2)],
    rounds: [],
    ...overrides,
  };
}

function pairing(overrides: Partial<TournamentPairing> = {}): TournamentPairing {
  return {
    gameCode: 'G1',
    whiteId: 1,
    whiteName: 'Alice',
    blackId: 2,
    blackName: 'Bob',
    status: 'IN_PROGRESS',
    result: null,
    ...overrides,
  };
}

describe('tournament-state', () => {
  it('formats half points with a decimal comma', () => {
    expect(formatScore(0)).toBe('0');
    expect(formatScore(1)).toBe('1');
    expect(formatScore(2.5)).toBe('2,5');
  });

  it('has no winner until the tournament is finished', () => {
    expect(winners(tournament())).toEqual([]);
  });

  it('returns the single winner of a finished tournament', () => {
    const t = tournament({ status: 'FINISHED' });
    expect(winners(t).map((s) => s.name)).toEqual(['Alice']);
  });

  it('returns every player tied for first place', () => {
    const t = tournament({
      status: 'FINISHED',
      standings: [standing(1, 'Alice', 2, 1), standing(2, 'Bob', 2, 1), standing(3, 'Cleo', 1, 3)],
    });
    expect(winners(t).map((s) => s.name)).toEqual(['Alice', 'Bob']);
  });

  it('knows whether a user is registered', () => {
    expect(isRegistered(tournament(), 2)).toBeTrue();
    expect(isRegistered(tournament(), 99)).toBeFalse();
    expect(isRegistered(tournament(), null)).toBeFalse();
  });

  it('knows whether a user plays a pairing', () => {
    expect(isPlayerOf(pairing(), 1)).toBeTrue();
    expect(isPlayerOf(pairing(), 2)).toBeTrue();
    expect(isPlayerOf(pairing(), 3)).toBeFalse();
    expect(isPlayerOf(pairing(), null)).toBeFalse();
  });

  it('describes the result of a pairing', () => {
    expect(pairingResult(pairing())).toBe('en cours');
    expect(pairingResult(pairing({ status: 'FINISHED', result: '1-0' }))).toBe('1 - 0');
    expect(pairingResult(pairing({ status: 'FINISHED', result: '0-1' }))).toBe('0 - 1');
    expect(pairingResult(pairing({ status: 'FINISHED', result: '1/2-1/2' }))).toBe('½ - ½');
  });
});
