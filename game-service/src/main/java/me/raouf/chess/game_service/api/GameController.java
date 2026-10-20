package me.raouf.chess.game_service.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import me.raouf.chess.game_service.service.GamePlayService;

@RestController
public class GameController {

	private final GamePlayService games;

	public GameController(GamePlayService games) {
		this.games = games;
	}

	@PostMapping("/games")
	@ResponseStatus(HttpStatus.CREATED)
	public GameResponse create(@AuthenticationPrincipal Jwt jwt) {
		return GameResponse.from(games.createGame(userId(jwt)));
	}

	@GetMapping("/games/{code}")
	public GameResponse get(@PathVariable String code) {
		return GameResponse.from(games.getGame(code));
	}

	@PostMapping("/games/{code}/join")
	public GameResponse join(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
		return GameResponse.from(games.joinGame(code, userId(jwt)));
	}

	@PostMapping("/games/{code}/moves")
	public GameResponse play(
			@PathVariable String code,
			@Valid @RequestBody PlayMoveRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return GameResponse.from(games.playMove(code, request.uci(), userId(jwt)));
	}

	@PostMapping("/games/{code}/resign")
	public GameResponse resign(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
		return GameResponse.from(games.resign(code, userId(jwt)));
	}

	@GetMapping("/games/{code}/moves")
	public List<MoveResponse> moves(@PathVariable String code) {
		return games.getMoves(code).stream().map(MoveResponse::from).toList();
	}

	private long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
