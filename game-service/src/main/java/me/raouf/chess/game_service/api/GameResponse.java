package me.raouf.chess.game_service.api;

import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.User;

public record GameResponse(
		String code,
		String fen,
		String status,
		int nextMoveNumber,
		Long whiteId,
		String whiteName,
		Long blackId,
		String blackName,
		String turn,
		String result,
		String endReason,
		String tournamentCode,
		Integer round,
		Long initialMs,
		Long whiteRemainingMs,
		Long blackRemainingMs,
		Long clockDeadline,
		Long drawOfferedBy
) {

	public static GameResponse from(Game game) {
		return new GameResponse(
				game.getCode(),
				game.getCurrentFen(),
				game.getStatus().name(),
				game.getNextMoveNumber(),
				idOf(game.getWhiteUser()),
				nameOf(game.getWhiteUser()),
				idOf(game.getBlackUser()),
				nameOf(game.getBlackUser()),
				game.getTurn(),
				game.getResult(),
				game.getEndReason(),
				game.getTournament() == null ? null : game.getTournament().getCode(),
				game.getRound(),
				game.getInitialMs(),
				game.getWhiteRemainingMs(),
				game.getBlackRemainingMs(),
				game.getClockDeadline() == null ? null : game.getClockDeadline().toEpochMilli(),
				game.getDrawOfferedBy() == null ? null : game.getDrawOfferedBy().getId()
		);
	}

	private static Long idOf(User user) {
		return user == null ? null : user.getId();
	}

	private static String nameOf(User user) {
		return user == null ? null : user.getDisplayName();
	}

}
