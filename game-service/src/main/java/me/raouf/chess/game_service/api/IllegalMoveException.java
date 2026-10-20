package me.raouf.chess.game_service.api;

public class IllegalMoveException extends RuntimeException {

	public static final String CODE = "ILLEGAL_MOVE";

	public IllegalMoveException() {
		super(CODE);
	}

}
