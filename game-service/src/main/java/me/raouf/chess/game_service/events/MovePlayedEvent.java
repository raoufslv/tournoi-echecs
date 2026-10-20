package me.raouf.chess.game_service.events;

public record MovePlayedEvent(
		String type,
		long gameId,
		String code,
		int moveNumber,
		String uci,
		String san,
		String fenAfter,
		String playedBy
) {

	public static final String TYPE = "move.played";

	public static MovePlayedEvent of(long gameId, String code, int moveNumber, String uci, String san,
			String fenAfter, String playedBy) {
		return new MovePlayedEvent(TYPE, gameId, code, moveNumber, uci, san, fenAfter, playedBy);
	}

}
