package me.raouf.chess.game_service.api;

import me.raouf.chess.game_service.domain.Tournament;

public record MyTournamentResponse(
		String code,
		String name,
		String status,
		int roundsTotal,
		int currentRound,
		boolean organizer
) {

	public static MyTournamentResponse from(Tournament tournament, long userId) {
		return new MyTournamentResponse(
				tournament.getCode(),
				tournament.getName(),
				tournament.getStatus().name(),
				tournament.getRoundsTotal(),
				tournament.getCurrentRound(),
				tournament.getCreatedBy().getId().equals(userId));
	}

}
