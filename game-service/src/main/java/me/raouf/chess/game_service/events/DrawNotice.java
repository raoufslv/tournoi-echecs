package me.raouf.chess.game_service.events;

public record DrawNotice(String type, long gameId, String code, Long offeredBy) {

	public static final String OFFERED = "draw.offered";
	public static final String DECLINED = "draw.declined";

	public static DrawNotice offered(long gameId, String code, long offeredBy) {
		return new DrawNotice(OFFERED, gameId, code, offeredBy);
	}

	public static DrawNotice declined(long gameId, String code) {
		return new DrawNotice(DECLINED, gameId, code, null);
	}

}
