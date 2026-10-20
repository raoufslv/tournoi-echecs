package me.raouf.chess.game_service.service;

import java.security.SecureRandom;
import java.util.List;

import com.github.bhlangonijr.chesslib.Board;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.api.GameNotFoundException;
import me.raouf.chess.game_service.api.IllegalMoveException;
import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.GameRepository;
import me.raouf.chess.game_service.domain.GameStatus;
import me.raouf.chess.game_service.domain.Move;
import me.raouf.chess.game_service.domain.MoveRepository;
import me.raouf.chess.game_service.domain.User;
import me.raouf.chess.game_service.domain.UserRepository;
import me.raouf.chess.game_service.events.GameEndedEvent;
import me.raouf.chess.game_service.events.MovePlayedEvent;
import me.raouf.chess.game_service.events.OutboxService;
import me.raouf.chess.game_service.events.PlayerJoinedEvent;

@Service
public class GamePlayService {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;

	private final GameRepository games;
	private final MoveRepository moves;
	private final UserRepository users;
	private final OutboxService outbox;
	private final SecureRandom random = new SecureRandom();

	public GamePlayService(GameRepository games, MoveRepository moves, UserRepository users,
			OutboxService outbox) {
		this.games = games;
		this.moves = moves;
		this.users = users;
		this.outbox = outbox;
	}

	@Transactional
	public Game createGame(long userId) {
		User creator = findUser(userId);
		Board board = new Board();
		String code = nextCode();
		while (games.findByCode(code).isPresent()) {
			code = nextCode();
		}
		Game game = new Game(code, GameStatus.WAITING, board.getFen(), 1);
		game.setWhiteUser(creator);
		return games.save(game);
	}

	@Transactional(readOnly = true)
	public Game getGame(String code) {
		return games.findByCode(code).orElseThrow(() -> new GameNotFoundException(code));
	}

	@Transactional
	public Game joinGame(String code, long userId) {
		Game game = games.findWithLockByCode(code).orElseThrow(() -> new GameNotFoundException(code));
		if (game.getStatus() != GameStatus.WAITING || game.getBlackUser() != null) {
			throw ApiException.gameFull();
		}
		if (game.getWhiteUser() != null && game.getWhiteUser().getId().equals(userId)) {
			throw ApiException.gameFull();
		}
		User black = findUser(userId);
		game.setBlackUser(black);
		game.setStatus(GameStatus.IN_PROGRESS);
		outbox.record(game.getId(), PlayerJoinedEvent.TYPE, null,
				PlayerJoinedEvent.of(game.getId(), game.getCode(), "black", black.getId(), black.getDisplayName()));
		return game;
	}

	@Transactional
	public Game playMove(String code, String uci, long userId) {
		Game game = games.findWithLockByCode(code).orElseThrow(() -> new GameNotFoundException(code));
		if (game.getStatus() == GameStatus.WAITING) {
			throw ApiException.gameNotStarted();
		}
		if (game.getStatus() == GameStatus.FINISHED) {
			throw new IllegalMoveException();
		}
		User expected = "WHITE".equals(game.getTurn()) ? game.getWhiteUser() : game.getBlackUser();
		if (expected == null || !expected.getId().equals(userId)) {
			throw ApiException.notYourTurn();
		}

		Board board = new Board();
		board.loadFromFen(game.getCurrentFen());
		com.github.bhlangonijr.chesslib.move.Move legal = findLegalMove(board, uci);
		if (legal == null) {
			throw new IllegalMoveException();
		}
		String playedBy = game.getTurn().toLowerCase();
		// Le SAN depend de la position d'avant le coup (ambiguite, prise, roque) : on le calcule avant doMove.
		String san = SanNotation.of(game.getCurrentFen(), legal);
		if (!board.doMove(legal)) {
			throw new IllegalMoveException();
		}

		int moveNumber = game.getNextMoveNumber();
		String fenAfter = board.getFen();
		moves.save(new Move(game, moveNumber, legal.toString(), san, fenAfter));
		game.setCurrentFen(fenAfter);
		game.setNextMoveNumber(moveNumber + 1);
		outbox.record(game.getId(), MovePlayedEvent.TYPE, moveNumber,
				MovePlayedEvent.of(game.getId(), game.getCode(), moveNumber, legal.toString(), san, fenAfter,
						playedBy));

		boolean mated = board.isMated();
		if (mated || board.isStaleMate()) {
			String result = mated ? winnerResult(playedBy) : "1/2-1/2";
			finishGame(game, moveNumber, result, mated ? "CHECKMATE" : "STALEMATE");
		}
		return game;
	}

	@Transactional
	public Game resign(String code, long userId) {
		Game game = games.findWithLockByCode(code).orElseThrow(() -> new GameNotFoundException(code));
		if (game.getStatus() == GameStatus.WAITING) {
			throw ApiException.gameNotStarted();
		}
		if (game.getStatus() == GameStatus.FINISHED) {
			throw ApiException.gameAlreadyFinished();
		}
		boolean isWhite = game.getWhiteUser() != null && game.getWhiteUser().getId().equals(userId);
		boolean isBlack = game.getBlackUser() != null && game.getBlackUser().getId().equals(userId);
		if (!isWhite && !isBlack) {
			throw ApiException.spectatorCannotMove();
		}
		// Celui qui abandonne perd : l'adversaire gagne.
		String winner = isWhite ? "black" : "white";
		finishGame(game, game.getNextMoveNumber() - 1, winnerResult(winner), "RESIGNATION");
		return game;
	}

	@Transactional(readOnly = true)
	public List<Move> getMoves(String code) {
		Game game = games.findByCode(code).orElseThrow(() -> new GameNotFoundException(code));
		return moves.findByGameOrderByMoveNumberAsc(game);
	}

	private void finishGame(Game game, int lastMoveNumber, String result, String reason) {
		game.finish(result, reason);
		outbox.record(game.getId(), GameEndedEvent.TYPE, lastMoveNumber,
				GameEndedEvent.of(game.getId(), game.getCode(), lastMoveNumber, result, reason,
						game.getCurrentFen()));
	}

	private static String winnerResult(String winnerColor) {
		return "white".equals(winnerColor) ? "1-0" : "0-1";
	}

	private User findUser(long userId) {
		return users.findById(userId).orElseThrow(ApiException::invalidCredentials);
	}

	private com.github.bhlangonijr.chesslib.move.Move findLegalMove(Board board, String uci) {
		String normalized = uci.trim().toLowerCase();
		for (com.github.bhlangonijr.chesslib.move.Move move : board.legalMoves()) {
			if (move.toString().equals(normalized)) {
				return move;
			}
		}
		return null;
	}

	private String nextCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
