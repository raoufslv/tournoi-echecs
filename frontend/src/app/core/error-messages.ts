const MESSAGES: Record<string, string> = {
  EMAIL_TAKEN: 'Cet email est déjà utilisé.',
  INVALID_CREDENTIALS: 'Email ou mot de passe incorrect.',
  GAME_FULL: 'Cette partie est déjà complète.',
  GAME_NOT_STARTED: "La partie n'a pas encore commencé : il manque un adversaire.",
  NOT_YOUR_TURN: "Ce n'est pas votre tour.",
  ILLEGAL_MOVE: 'Coup illégal.',
  GAME_ALREADY_FINISHED: 'La partie est déjà terminée.',
  SPECTATOR_CANNOT_MOVE: 'Seuls les joueurs de la partie peuvent abandonner.',
  GAME_NOT_FOUND: 'Partie introuvable.',
  TOURNAMENT_NOT_FOUND: 'Tournoi introuvable.',
  TOURNAMENT_NOT_OPEN: "Les inscriptions sont closes : le tournoi a déjà commencé.",
  TOURNAMENT_FULL: 'Ce tournoi est complet (8 joueurs maximum).',
  ALREADY_REGISTERED: 'Vous êtes déjà inscrit à ce tournoi.',
  NOT_ORGANIZER: "Seul l'organisateur peut lancer le tournoi.",
  NOT_ENOUGH_PLAYERS: 'Il faut au moins 4 joueurs pour lancer le tournoi.',
  TOURNAMENT_ALREADY_STARTED: 'Le tournoi a déjà été lancé.',
  INVALID_REQUEST: 'Les informations saisies sont invalides.',
};

const FALLBACK = 'Une erreur est survenue. Réessayez.';

/** Transforme une erreur HTTP (ou un code d'erreur API) en message lisible en français. */
export function errorMessage(error: unknown): string {
  if (typeof error === 'string') {
    return MESSAGES[error] ?? FALLBACK;
  }
  const e = error as { status?: number; error?: { code?: string } } | null;
  if (e?.status === 0) {
    return 'Serveur injoignable. Vérifiez que le backend est démarré.';
  }
  const code = e?.error?.code;
  return (code && MESSAGES[code]) || FALLBACK;
}
