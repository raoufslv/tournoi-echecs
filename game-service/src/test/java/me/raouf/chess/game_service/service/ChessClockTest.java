package me.raouf.chess.game_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.GameStatus;

class ChessClockTest {

	private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	@Test
	void onlyAllowsFiveTenOrFifteenMinutes() {
		assertThat(ChessClock.requireMinutes(null)).isEqualTo(10);
		assertThat(ChessClock.requireMinutes(5)).isEqualTo(5);
		assertThatThrownBy(() -> ChessClock.requireMinutes(7)).isInstanceOf(ApiException.class);
	}

	@Test
	void consumeTurnChargesThePlayerWhoMoved() {
		Game game = new Game("ABC", GameStatus.IN_PROGRESS, START, 1);
		ChessClock.arm(game, 5);
		Instant start = Instant.parse("2026-01-01T00:00:00Z");
		ChessClock.start(game, start);

		ChessClock.consumeTurn(game, start.plusSeconds(3));

		assertThat(game.getWhiteRemainingMs()).isEqualTo(5 * 60_000L - 3_000);
		assertThat(game.getBlackRemainingMs()).isEqualTo(5 * 60_000L);
		assertThat(game.getClockDeadline()).isEqualTo(start.plusSeconds(3).plusMillis(5 * 60_000L));
	}

}
