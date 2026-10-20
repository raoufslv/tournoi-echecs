package me.raouf.chess.game_service.api;

import me.raouf.chess.game_service.domain.Move;

public record MoveResponse(int moveNumber, String uci, String san, String fenAfter) {

	public static MoveResponse from(Move move) {
		return new MoveResponse(move.getMoveNumber(), move.getUci(), move.getSan(), move.getFenAfter());
	}

}
