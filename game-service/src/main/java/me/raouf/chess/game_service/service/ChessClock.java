package me.raouf.chess.game_service.service;

import java.time.Duration;
import java.time.Instant;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.domain.Game;

/** Pendule sans incrément. Le serveur est la seule horloge qui peut faire tomber le drapeau. */
public final class ChessClock {

	private ChessClock() {
	}

	public static int requireMinutes(Integer minutes) {
		int value = minutes == null ? 10 : minutes;
		if (value != 5 && value != 10 && value != 15) {
			throw ApiException.invalidRequest();
		}
		return value;
	}

	public static void arm(Game game, int minutes) {
		long ms = minutes * 60_000L;
		game.setInitialMs(ms);
		game.setWhiteRemainingMs(ms);
		game.setBlackRemainingMs(ms);
	}

	/** Démarre le temps des Blancs. Sans effet si la partie n'a pas de pendule. */
	public static void start(Game game, Instant now) {
		if (game.getWhiteRemainingMs() == null) {
			return;
		}
		game.setClockDeadline(now.plusMillis(game.getWhiteRemainingMs()));
	}

	public static boolean expired(Game game, Instant now) {
		Instant deadline = game.getClockDeadline();
		return deadline != null && !now.isBefore(deadline);
	}

	/**
	 * Impute le temps écoulé au joueur au trait, puis arme l'autre.
	 * À appeler tant que le FEN désigne encore le joueur qui vient de jouer.
	 */
	public static void consumeTurn(Game game, Instant now) {
		if (game.getClockDeadline() == null || game.getWhiteRemainingMs() == null || game.getBlackRemainingMs() == null) {
			return;
		}
		long left = Math.max(0, Duration.between(now, game.getClockDeadline()).toMillis());
		boolean white = "WHITE".equals(game.getTurn());
		if (white) {
			game.setWhiteRemainingMs(left);
		}
		else {
			game.setBlackRemainingMs(left);
		}
		long next = white ? game.getBlackRemainingMs() : game.getWhiteRemainingMs();
		game.setClockDeadline(now.plusMillis(next));
	}

	public static void stop(Game game) {
		game.setClockDeadline(null);
	}

}
