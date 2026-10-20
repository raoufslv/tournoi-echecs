package me.raouf.chess.game_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> apiError(ApiException error) {
		return ResponseEntity.status(error.status()).body(new ErrorResponse(error.code()));
	}

	@ExceptionHandler(GameNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse gameNotFound() {
		return new ErrorResponse("GAME_NOT_FOUND");
	}

	@ExceptionHandler(IllegalMoveException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	public ErrorResponse illegalMove() {
		return new ErrorResponse(IllegalMoveException.CODE);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse invalidBody() {
		return new ErrorResponse("INVALID_REQUEST");
	}

}
