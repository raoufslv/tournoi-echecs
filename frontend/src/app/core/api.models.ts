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
  /** Renseignés pour une partie de tournoi : permettent le lien « Retour au tournoi ». */
  tournamentCode?: string | null;
  round?: number | null;
}

export type TournamentStatus = 'REGISTRATION' | 'IN_PROGRESS' | 'FINISHED';

export interface Standing {
  rank: number;
  userId: number;
  name: string;
  score: number;
  buchholz: number;
  hadBye: boolean;
}

export interface TournamentPairing {
  gameCode: string;
  whiteId: number | null;
  whiteName: string | null;
  blackId: number | null;
  blackName: string | null;
  status: GameStatus;
  result: string | null;
}

export interface TournamentRound {
  round: number;
  games: TournamentPairing[];
  byeUserId: number | null;
  byeName: string | null;
}

export interface Tournament {
  code: string;
  name: string;
  status: TournamentStatus;
  roundsTotal: number;
  currentRound: number;
  organizerId: number;
  organizerName: string;
  standings: Standing[];
  rounds: TournamentRound[];
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
