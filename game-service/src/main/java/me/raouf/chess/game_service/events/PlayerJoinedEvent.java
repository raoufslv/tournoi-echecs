package me.raouf.chess.game_service.events;

public record PlayerJoinedEvent(
		String type,
		long gameId,
		String code,
		String color,
		long userId,
		String displayName
) {

	public static final String TYPE = "player.joined";

	public static PlayerJoinedEvent of(long gameId, String code, String color, long userId, String displayName) {
		return new PlayerJoinedEvent(TYPE, gameId, code, color, userId, displayName);
	}

}
