package me.raouf.chess.game_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

import org.junit.jupiter.api.Test;

class SanNotationTests {

	private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/** Cherche le coup legal correspondant a l'UCI dans la position, comme le fait GamePlayService. */
	private static Move legal(String fen, String uci) {
		Board board = new Board();
		board.loadFromFen(fen);
		return board.legalMoves().stream()
				.filter(m -> m.toString().equals(uci))
				.findFirst()
				.orElseThrow(() -> new AssertionError("coup illegal dans le test: " + uci));
	}

	private static String san(String fen, String uci) {
		return SanNotation.of(fen, legal(fen, uci));
	}

	@Test
	void pawnMove() {
		assertThat(san(START, "e2e4")).isEqualTo("e4");
	}

	@Test
	void pieceMove() {
		assertThat(san(START, "g1f3")).isEqualTo("Nf3");
	}

	@Test
	void pawnCapture() {
		String fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2";
		assertThat(san(fen, "e4d5")).isEqualTo("exd5");
	}

	@Test
	void kingsideCastling() {
		String fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4";
		assertThat(san(fen, "e1g1")).isEqualTo("O-O");
	}

	@Test
	void promotion() {
		assertThat(san("7k/4P3/8/8/8/8/8/K7 w - - 0 1", "e7e8q")).isEqualTo("e8=Q+");
	}

	@Test
	void checkAndMateMarkers() {
		assertThat(san("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2", "d8h4"))
				.isEqualTo("Qh4#");
		assertThat(san("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a8")).isEqualTo("Ra8+");
	}

	@Test
	void ambiguityIsResolvedWithTheFile() {
		// Deux cavaliers peuvent aller en d2 : il faut preciser la colonne de depart.
		String fen = "4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1";
		assertThat(san(fen, "b1d2")).isEqualTo("Nbd2");
		assertThat(san(fen, "f1d2")).isEqualTo("Nfd2");
	}

}
