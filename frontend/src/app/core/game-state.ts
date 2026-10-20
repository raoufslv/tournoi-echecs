import { Game, GameEvent, MoveEntry, MovePlayedEvent } from './api.models';

export const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

function turnOf(fen: string): 'WHITE' | 'BLACK' {
  return fen.split(' ')[1] === 'w' ? 'WHITE' : 'BLACK';
}

/**
 * Applique un événement du direct à l'état affiché.
 * Un événement déjà connu (rejeu, doublon, écho de mon propre coup) renvoie l'état inchangé.
 */
export function applyEvent(game: Game, event: GameEvent): Game {
  switch (event.type) {
    case 'move.played':
      if (event.moveNumber < game.nextMoveNumber) {
        return game;
      }
      return {
        ...game,
        fen: event.fenAfter,
        nextMoveNumber: event.moveNumber + 1,
        turn: turnOf(event.fenAfter),
      };
    case 'player.joined':
      if (game.blackId !== null) {
        return game;
      }
      return {
        ...game,
        blackId: event.userId,
        blackName: event.displayName,
        status: game.status === 'WAITING' ? 'IN_PROGRESS' : game.status,
      };
    case 'game.ended':
      if (game.status === 'FINISHED' && game.result === event.result) {
        return game;
      }
      return { ...game, status: 'FINISHED', result: event.result, endReason: event.endReason };
  }
}

/** Vrai si `incoming` apporte quelque chose de plus récent que `current` (réponse REST ou GET de rattrapage). */
export function isNewer(current: Game | null, incoming: Game): boolean {
  if (!current) {
    return true;
  }
  if (incoming.nextMoveNumber !== current.nextMoveNumber) {
    return incoming.nextMoveNumber > current.nextMoveNumber;
  }
  return (
    incoming.status !== current.status ||
    incoming.blackId !== current.blackId ||
    incoming.result !== current.result
  );
}

/**
 * Ajoute à l'historique le coup annoncé par le direct.
 * Renvoie la même liste pour un doublon, la liste complétée pour le coup suivant,
 * et null s'il manque des coups : l'appelant doit alors recharger l'historique par REST.
 */
export function appendMove(moves: MoveEntry[], event: MovePlayedEvent): MoveEntry[] | null {
  if (event.moveNumber <= moves.length) {
    return moves;
  }
  if (event.moveNumber !== moves.length + 1) {
    return null;
  }
  return [
    ...moves,
    { moveNumber: event.moveNumber, uci: event.uci, san: event.san ?? null, fenAfter: event.fenAfter },
  ];
}

/** Position affichée après `index` coups (0 = position de départ). */
export function fenAtIndex(moves: MoveEntry[], index: number): string {
  if (index <= 0) {
    return START_FEN;
  }
  return moves[Math.min(index, moves.length) - 1]?.fenAfter ?? START_FEN;
}

/** Phrase de fin de partie, ou null si le résultat n'est pas connu (parties anciennes). */
export function resultText(result: string | null, endReason: string | null): string | null {
  if (!result) {
    return null;
  }
  const reasons: Record<string, string> = {
    CHECKMATE: 'par mat',
    RESIGNATION: 'par abandon',
    STALEMATE: 'par pat',
  };
  const reason = endReason ? reasons[endReason] : undefined;
  const suffix = reason ? ` ${reason}` : '';
  if (result === '1-0') {
    return `Les Blancs gagnent${suffix}`;
  }
  if (result === '0-1') {
    return `Les Noirs gagnent${suffix}`;
  }
  return `Partie nulle${suffix}`;
}
