package me.raouf.chess.game_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import me.raouf.chess.game_service.service.SwissPairing.Meeting;
import me.raouf.chess.game_service.service.SwissPairing.Pairing;
import me.raouf.chess.game_service.service.SwissPairing.Player;
import me.raouf.chess.game_service.service.SwissPairing.Result;

class SwissPairingTests {

	private static Player p(long id, int halfPoints) {
		return new Player(id, halfPoints, false, 0);
	}

	private static Set<Long> idsOf(Result result) {
		Set<Long> ids = new HashSet<>();
		for (Pairing pairing : result.pairings()) {
			ids.add(pairing.whiteId());
			ids.add(pairing.blackId());
		}
		return ids;
	}

	@Test
	void firstRoundPairsPlayersInTheOrderGiven() {
		Result result = SwissPairing.pair(List.of(p(1, 0), p(2, 0), p(3, 0), p(4, 0)), Set.of());

		assertThat(result.byeId()).isNull();
		assertThat(result.pairings()).containsExactly(new Pairing(1, 2), new Pairing(3, 4));
	}

	@Test
	void pairsPlayersWithClosestScoresTogether() {
		Result result = SwissPairing.pair(List.of(p(1, 0), p(2, 2), p(3, 2), p(4, 0)), Set.of());

		assertThat(result.pairings()).extracting(x -> Set.of(x.whiteId(), x.blackId()))
				.containsExactlyInAnyOrder(Set.of(2L, 3L), Set.of(1L, 4L));
	}

	@Test
	void avoidsRematchesWhenAnotherPairingExists() {
		Set<Meeting> played = Set.of(Meeting.of(1, 2), Meeting.of(3, 4));

		Result result = SwissPairing.pair(List.of(p(1, 2), p(2, 2), p(3, 0), p(4, 0)), played);

		assertThat(result.pairings()).extracting(x -> Set.of(x.whiteId(), x.blackId()))
				.noneMatch(pair -> pair.equals(Set.of(1L, 2L)) || pair.equals(Set.of(3L, 4L)));
	}

	@Test
	void backtracksWhenTheGreedyChoiceLeadsToADeadEnd() {
		// Le premier choix possible pour 1 est 3, mais il laisserait 2-4 (deja jouee) : impasse.
		// Il faut revenir en arriere et choisir 1-4 puis 2-3.
		Set<Meeting> played = Set.of(Meeting.of(1, 2), Meeting.of(2, 4));

		Result result = SwissPairing.pair(List.of(p(1, 4), p(2, 3), p(3, 2), p(4, 1)), played);

		assertThat(result.pairings()).extracting(x -> Set.of(x.whiteId(), x.blackId()))
				.containsExactlyInAnyOrder(Set.of(1L, 4L), Set.of(2L, 3L));
	}

	@Test
	void oddPlayerCountGivesTheByeToTheLowestRankedPlayer() {
		Result result = SwissPairing.pair(List.of(p(1, 4), p(2, 3), p(3, 2), p(4, 1), p(5, 0)), Set.of());

		assertThat(result.byeId()).isEqualTo(5L);
		assertThat(result.pairings()).hasSize(2);
		assertThat(idsOf(result)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
	}

	@Test
	void aPlayerNeverGetsTwoByesWhenAnotherCandidateExists() {
		List<Player> players = List.of(p(1, 4), p(2, 3), p(3, 2), new Player(4, 1, true, 0), new Player(5, 0, true, 0));

		Result result = SwissPairing.pair(players, Set.of());

		assertThat(result.byeId()).isEqualTo(3L);
	}

	@Test
	void whiteGoesToThePlayerWhoHadFewerWhites() {
		Player moreWhites = new Player(1, 2, false, 2);
		Player moreBlacks = new Player(2, 0, false, -2);

		Result result = SwissPairing.pair(List.of(moreWhites, moreBlacks), Set.of());

		assertThat(result.pairings()).containsExactly(new Pairing(2, 1));
	}

	@Test
	void higherRankedPlayerGetsWhiteOnEqualColorBalance() {
		Result result = SwissPairing.pair(List.of(p(1, 2), p(2, 0)), Set.of());

		assertThat(result.pairings()).containsExactly(new Pairing(1, 2));
	}

	@Test
	void allowsRematchesRatherThanFailingWhenNoFreshPairingExists() {
		// Tout le monde a deja joue tout le monde.
		Set<Meeting> played = Set.of(Meeting.of(1, 2), Meeting.of(1, 3), Meeting.of(1, 4), Meeting.of(2, 3),
				Meeting.of(2, 4), Meeting.of(3, 4));

		Result result = SwissPairing.pair(List.of(p(1, 0), p(2, 0), p(3, 0), p(4, 0)), played);

		assertThat(result.pairings()).hasSize(2);
		assertThat(idsOf(result)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
	}

	@Test
	void everyPlayerAppearsExactlyOnce() {
		List<Player> players = List.of(p(1, 3), p(2, 3), p(3, 2), p(4, 2), p(5, 1), p(6, 1), p(7, 0), p(8, 0));

		Result result = SwissPairing.pair(players, Set.of());

		assertThat(result.pairings()).hasSize(4);
		assertThat(idsOf(result)).hasSize(8);
	}

	@Test
	void rejectsFewerThanTwoPlayers() {
		assertThatThrownBy(() -> SwissPairing.pair(List.of(p(1, 0)), Set.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
