package me.raouf.chess.live;

import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Flux en lecture seule, sans JWT : EventSource ne sait pas envoyer d'en-tete Authorization,
 * et le code de partie sert de secret de partage.
 */
@RestController
public class WatchController {

	private final WatchRegistry registry;

	public WatchController(WatchRegistry registry) {
		this.registry = registry;
	}

	@GetMapping(path = "/games/{code}/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter watch(@PathVariable String code) {
		return registry.subscribe(code.trim().toUpperCase(Locale.ROOT));
	}

	@GetMapping(path = "/tournaments/{code}/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter watchTournament(@PathVariable String code) {
		return registry.subscribe(WatchRegistry.tournamentChannel(code.trim().toUpperCase(Locale.ROOT)));
	}

}
