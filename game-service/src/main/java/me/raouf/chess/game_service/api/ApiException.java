package me.raouf.chess.game_service.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code) {
		super(code);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
	}

	public static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
	}

	public static ApiException gameFull() {
		return new ApiException(HttpStatus.CONFLICT, "GAME_FULL");
	}

	public static ApiException gameNotStarted() {
		return new ApiException(HttpStatus.CONFLICT, "GAME_NOT_STARTED");
	}

	public static ApiException gameAlreadyFinished() {
		return new ApiException(HttpStatus.CONFLICT, "GAME_ALREADY_FINISHED");
	}

	public static ApiException spectatorCannotMove() {
		return new ApiException(HttpStatus.FORBIDDEN, "SPECTATOR_CANNOT_MOVE");
	}

	public static ApiException notYourTurn() {
		return new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_TURN");
	}

	public static ApiException tournamentNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND");
	}

	public static ApiException tournamentNotOpen() {
		return new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_NOT_OPEN");
	}

	public static ApiException tournamentFull() {
		return new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_FULL");
	}

	public static ApiException alreadyRegistered() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED");
	}

	public static ApiException notOrganizer() {
		return new ApiException(HttpStatus.FORBIDDEN, "NOT_ORGANIZER");
	}

	public static ApiException notEnoughPlayers() {
		return new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS");
	}

	public static ApiException tournamentAlreadyStarted() {
		return new ApiException(HttpStatus.CONFLICT, "TOURNAMENT_ALREADY_STARTED");
	}

}
