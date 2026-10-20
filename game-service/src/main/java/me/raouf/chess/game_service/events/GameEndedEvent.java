package me.raouf.chess.game_service.events;

public record GameEndedEvent(
		String type,
		long gameId,
		String code,
		int moveNumber,
		String result,
		String endReason,
		String finalFen
) {

	public static final String TYPE = "game.ended";

	public static GameEndedEvent of(long gameId, String code, int moveNumber, String result, String endReason,
			String finalFen) {
		return new GameEndedEvent(TYPE, gameId, code, moveNumber, result, endReason, finalFen);
	}

}
