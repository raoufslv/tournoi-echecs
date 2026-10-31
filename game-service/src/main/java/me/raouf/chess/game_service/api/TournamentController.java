package me.raouf.chess.game_service.api;

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
import me.raouf.chess.game_service.service.TournamentService;

@RestController
public class TournamentController {

	private final TournamentService tournaments;
	private final GamePlayService games;

	public TournamentController(TournamentService tournaments, GamePlayService games) {
		this.tournaments = tournaments;
		this.games = games;
	}

	@GetMapping("/tournaments/mine")
	public java.util.List<MyTournamentResponse> mine(@AuthenticationPrincipal Jwt jwt) {
		return tournaments.listMine(userId(jwt));
	}

	@PostMapping("/tournaments")
	@ResponseStatus(HttpStatus.CREATED)
	public TournamentResponse create(@Valid @RequestBody CreateTournamentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		var tournament = tournaments.create(userId(jwt), request.name(), request.rounds(), request.minutes());
		return TournamentResponse.from(tournaments.get(tournament.getCode()));
	}

	@GetMapping("/tournaments/{code}")
	public TournamentResponse get(@PathVariable String code) {
		return TournamentResponse.from(tournaments.get(code));
	}

	@PostMapping("/tournaments/{code}/join")
	public TournamentResponse join(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
		tournaments.join(code, userId(jwt));
		return TournamentResponse.from(tournaments.get(code));
	}

	@PostMapping("/tournaments/{code}/start")
	public TournamentResponse start(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
		tournaments.start(code, userId(jwt));
		return TournamentResponse.from(tournaments.get(code));
	}

	@PostMapping("/tournaments/{code}/games/{gameCode}/forfeit")
	public GameResponse forfeit(
			@PathVariable String code,
			@PathVariable String gameCode,
			@Valid @RequestBody ForfeitRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return GameResponse.from(games.forfeit(code, gameCode, request.color(), userId(jwt)));
	}

	private long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
