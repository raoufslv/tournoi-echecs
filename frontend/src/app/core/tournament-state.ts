import { Standing, Tournament, TournamentPairing } from './api.models';

/** 1 -> "1", 1.5 -> "1,5" (le serveur ne renvoie que des demi-points). */
export function formatScore(score: number): string {
  return String(score).replace('.', ',');
}

/** Premiers du classement (plusieurs en cas d'égalité parfaite) ; vide tant que rien n'est joué. */
export function winners(tournament: Tournament): Standing[] {
  if (tournament.status !== 'FINISHED') {
    return [];
  }
  return tournament.standings.filter((s) => s.rank === 1);
}

export function isRegistered(tournament: Tournament, userId: number | null): boolean {
  return userId !== null && tournament.standings.some((s) => s.userId === userId);
}

export function isPlayerOf(pairing: TournamentPairing, userId: number | null): boolean {
  return userId !== null && (pairing.whiteId === userId || pairing.blackId === userId);
}

/** "1-0" -> "1 - 0", "1/2-1/2" -> "½ - ½" ; une partie en cours n'a pas de résultat. */
export function pairingResult(pairing: TournamentPairing): string {
  switch (pairing.result) {
    case '1-0':
      return '1 - 0';
    case '0-1':
      return '0 - 1';
    case '1/2-1/2':
      return '½ - ½';
    default:
      return pairing.status === 'FINISHED' ? '' : 'en cours';
  }
}
