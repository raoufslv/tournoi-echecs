package me.raouf.chess.game_service.events;

public record RematchCreatedEvent(String type, long gameId, String code, String rematchCode) {

	public static final String TYPE = "rematch.created";

	public static RematchCreatedEvent of(long gameId, String code, String rematchCode) {
		return new RematchCreatedEvent(TYPE, gameId, code, rematchCode);
	}

}
