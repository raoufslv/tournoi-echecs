export type GameStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED';
export type Turn = 'WHITE' | 'BLACK';

export interface Game {
  code: string;
  fen: string;
  status: GameStatus;
  nextMoveNumber: number;
  whiteId: number | null;
  whiteName: string | null;
  blackId: number | null;
  blackName: string | null;
  turn: Turn;
  /** `1-0`, `0-1` ou `1/2-1/2`, renseigné quand la partie est terminée. */
  result: string | null;
  endReason: string | null;
}

export interface MoveEntry {
  moveNumber: number;
  uci: string;
  /** Absent pour les coups joués avant l'introduction de la notation SAN. */
  san: string | null;
  fenAfter: string;
}

export interface MovePlayedEvent {
  type: 'move.played';
  code: string;
  moveNumber: number;
  uci: string;
  san?: string | null;
  fenAfter: string;
  playedBy: 'white' | 'black';
}

export interface GameEndedEvent {
  type: 'game.ended';
  code: string;
  moveNumber: number;
  result: string;
  endReason: string;
  finalFen: string;
}

export interface PlayerJoinedEvent {
  type: 'player.joined';
  code: string;
  color: 'white' | 'black';
  userId: number;
  displayName: string;
}

export type GameEvent = MovePlayedEvent | GameEndedEvent | PlayerJoinedEvent;

export interface TokenResponse {
  token: string;
}

export interface ApiError {
  code: string;
}
