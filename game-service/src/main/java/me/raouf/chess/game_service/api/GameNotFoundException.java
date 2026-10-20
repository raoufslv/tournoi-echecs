package me.raouf.chess.game_service.api;

public class GameNotFoundException extends RuntimeException {

	public GameNotFoundException(String code) {
		super(code);
	}

}
