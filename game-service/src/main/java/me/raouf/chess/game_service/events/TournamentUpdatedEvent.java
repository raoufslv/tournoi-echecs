package me.raouf.chess.game_service.events;

/** Signal leger : le front recharge le tournoi par REST, donc l'ordre exact des messages n'importe pas. */
public record TournamentUpdatedEvent(
		String type,
		long tournamentId,
		String code,
		String status,
		int currentRound
) {

	public static final String TYPE = "tournament.updated";

	public static TournamentUpdatedEvent of(long tournamentId, String code, String status, int currentRound) {
		return new TournamentUpdatedEvent(TYPE, tournamentId, code, status, currentRound);
	}

}
