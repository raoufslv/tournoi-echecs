export const PAGE_TITLE = "Tournoi d'échecs";
export const OPPONENT_MOVED_TITLE = "Coup reçu — Tournoi d'échecs";

/** Vrai seulement pour un coup adverse reçu pendant que l'onglet est en arrière-plan. */
export function shouldNotifyOpponentMove(
  hidden: boolean,
  playedBy: 'white' | 'black',
  mySide: 'white' | 'black' | null,
): boolean {
  return hidden && mySide !== null && playedBy !== mySide;
}

export function titleAfterMove(notify: boolean): string {
  return notify ? OPPONENT_MOVED_TITLE : PAGE_TITLE;
}
