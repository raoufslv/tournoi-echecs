import { Game, MoveEntry } from './api.models';

/** Partie au format PGN, à partir des coups déjà affichés. */
export function toPgn(
  game: Pick<Game, 'code' | 'whiteName' | 'blackName' | 'result'>,
  moves: MoveEntry[],
): string {
  const result = game.result ?? '*';
  const lines = [
    `[Event "Partie ${game.code}"]`,
    `[Site "Tournoi d'echecs"]`,
    `[White "${tag(game.whiteName ?? '?')}"]`,
    `[Black "${tag(game.blackName ?? '?')}"]`,
    `[Result "${result}"]`,
    '',
    movetext(moves, result),
  ];
  return `${lines.join('\n')}\n`;
}

export function downloadPgn(filename: string, text: string): void {
  const blob = new Blob([text], { type: 'application/x-chess-pgn' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function tag(value: string): string {
  return value.replaceAll('"', '');
}

function movetext(moves: MoveEntry[], result: string): string {
  const parts: string[] = [];
  for (let i = 0; i < moves.length; i += 2) {
    const number = Math.floor(i / 2) + 1;
    const white = notation(moves[i]);
    const black = moves[i + 1] ? notation(moves[i + 1]) : '';
    parts.push(black ? `${number}. ${white} ${black}` : `${number}. ${white}`);
  }
  return `${parts.join(' ')} ${result}`.trim();
}

function notation(move: MoveEntry): string {
  return move.san ?? move.uci;
}
