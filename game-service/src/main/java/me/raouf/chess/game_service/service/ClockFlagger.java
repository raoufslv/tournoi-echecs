package me.raouf.chess.game_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import me.raouf.chess.game_service.domain.GameRepository;
import me.raouf.chess.game_service.domain.GameStatus;

/** Fait tomber le drapeau même si le joueur au trait n'envoie plus de requête. */
@Service
public class ClockFlagger {

	private final GameRepository games;
	private final GamePlayService play;

	public ClockFlagger(GameRepository games, GamePlayService play) {
		this.games = games;
		this.play = play;
	}

	@Scheduled(fixedDelay = 1000)
	public void flagDue() {
		List<String> codes = games.findCodesPastDeadline(GameStatus.IN_PROGRESS, Instant.now());
		for (String code : codes) {
			play.flagIfExpired(code);
		}
	}

}
