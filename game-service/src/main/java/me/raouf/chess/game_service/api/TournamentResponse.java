package me.raouf.chess.game_service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.GameStatus;
import me.raouf.chess.game_service.domain.Tournament;
import me.raouf.chess.game_service.domain.TournamentParticipant;
import me.raouf.chess.game_service.service.TournamentDetails;

public record TournamentResponse(
		String code,
		String name,
		String status,
		int roundsTotal,
		int currentRound,
		long organizerId,
		String organizerName,
		List<Standing> standings,
		List<RoundInfo> rounds
) {

	public record Standing(int rank, long userId, String name, double score, double buchholz, boolean hadBye) {
	}

	public record Pairing(String gameCode, Long whiteId, String whiteName, Long blackId, String blackName,
			String status, String result) {
	}

	public record RoundInfo(int round, List<Pairing> games, Long byeUserId, String byeName) {
	}

	public static TournamentResponse from(TournamentDetails details) {
		Tournament tournament = details.tournament();
		return new TournamentResponse(
				tournament.getCode(),
				tournament.getName(),
				tournament.getStatus().name(),
				tournament.getRoundsTotal(),
				tournament.getCurrentRound(),
				tournament.getCreatedBy().getId(),
				tournament.getCreatedBy().getDisplayName(),
				standings(details),
				rounds(details));
	}

	private static List<Standing> standings(TournamentDetails details) {
		Map<Long, Integer> halfPoints = new HashMap<>();
		details.participants().forEach(p -> halfPoints.put(p.getUser().getId(), p.getHalfPoints()));

		// Buchholz : somme des scores des adversaires deja affrontes (parties terminees).
		Map<Long, Integer> buchholz = new HashMap<>();
		for (Game game : details.games()) {
			if (game.getStatus() != GameStatus.FINISHED) {
				continue;
			}
			long white = game.getWhiteUser().getId();
			long black = game.getBlackUser().getId();
			buchholz.merge(white, halfPoints.get(black), Integer::sum);
			buchholz.merge(black, halfPoints.get(white), Integer::sum);
		}

		List<TournamentParticipant> sorted = new ArrayList<>(details.participants());
		sorted.sort(Comparator
				.comparingInt(TournamentParticipant::getHalfPoints).reversed()
				.thenComparing(Comparator.comparingInt(
						(TournamentParticipant p) -> buchholz.getOrDefault(p.getUser().getId(), 0)).reversed())
				.thenComparing(TournamentParticipant::getJoinedAt));

		List<Standing> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			TournamentParticipant p = sorted.get(i);
			int buch = buchholz.getOrDefault(p.getUser().getId(), 0);
			int rank = i + 1;
			if (i > 0) {
				// A egalite de score et de Buchholz, meme rang que le precedent.
				TournamentParticipant previous = sorted.get(i - 1);
				if (previous.getHalfPoints() == p.getHalfPoints()
						&& buchholz.getOrDefault(previous.getUser().getId(), 0) == buch) {
					rank = result.get(i - 1).rank();
				}
			}
			result.add(new Standing(rank, p.getUser().getId(), p.getUser().getDisplayName(),
					p.getHalfPoints() / 2.0, buch / 2.0, p.hadBye()));
		}
		return result;
	}

	private static List<RoundInfo> rounds(TournamentDetails details) {
		List<RoundInfo> rounds = new ArrayList<>();
		for (int round = 1; round <= details.tournament().getCurrentRound(); round++) {
			int current = round;
			List<Pairing> pairings = details.games().stream()
					.filter(g -> g.getRound() != null && g.getRound() == current)
					.map(g -> new Pairing(
							g.getCode(),
							g.getWhiteUser().getId(), g.getWhiteUser().getDisplayName(),
							g.getBlackUser().getId(), g.getBlackUser().getDisplayName(),
							g.getStatus().name(), g.getResult()))
					.toList();
			TournamentParticipant bye = details.participants().stream()
					.filter(p -> p.getByeRound() != null && p.getByeRound() == current)
					.findFirst().orElse(null);
			rounds.add(new RoundInfo(round, pairings,
					bye == null ? null : bye.getUser().getId(),
					bye == null ? null : bye.getUser().getDisplayName()));
		}
		return rounds;
	}

}
