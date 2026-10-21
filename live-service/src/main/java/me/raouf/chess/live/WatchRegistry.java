package me.raouf.chess.live;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Garde les connexions SSE ouvertes, regroupees par code de partie. */
@Component
public class WatchRegistry {

	private static final Logger log = LoggerFactory.getLogger(WatchRegistry.class);
	private static final String TOURNAMENT_PREFIX = "t:";

	private final Map<String, Set<SseEmitter>> watchers = new ConcurrentHashMap<>();
	private final long timeoutMs;

	public WatchRegistry(@Value("${app.sse.timeout-ms:3600000}") long timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	/** Canal d'un tournoi : prefixe pour ne jamais le confondre avec le code d'une partie. */
	public static String tournamentChannel(String code) {
		return TOURNAMENT_PREFIX + code;
	}

	public SseEmitter subscribe(String code) {
		SseEmitter emitter = new SseEmitter(timeoutMs);
		Set<SseEmitter> set = watchers.computeIfAbsent(code, k -> new CopyOnWriteArraySet<>());
		set.add(emitter);
		Runnable cleanup = () -> remove(code, emitter);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(e -> cleanup.run());
		try {
			// Envoie les en-tetes tout de suite : le navigateur considere alors la connexion ouverte.
			emitter.send(SseEmitter.event().comment("connected"));
		}
		catch (IOException e) {
			cleanup.run();
		}
		return emitter;
	}

	/** Envoie un evenement a tous les spectateurs de la partie. Renvoie le nombre de destinataires. */
	public int broadcast(String code, String type, String json) {
		Set<SseEmitter> set = watchers.get(code);
		if (set == null) {
			return 0;
		}
		int sent = 0;
		for (SseEmitter emitter : set) {
			try {
				emitter.send(SseEmitter.event().name(type).data(json));
				sent++;
			}
			catch (IOException | IllegalStateException e) {
				remove(code, emitter);
			}
		}
		return sent;
	}

	public int watcherCount(String code) {
		Set<SseEmitter> set = watchers.get(code);
		return set == null ? 0 : set.size();
	}

	/** Un commentaire periodique empeche proxys et navigateurs de couper une connexion silencieuse. */
	@Scheduled(fixedDelayString = "${app.sse.heartbeat-ms:25000}")
	void heartbeat() {
		watchers.forEach((code, set) -> {
			for (SseEmitter emitter : set) {
				try {
					emitter.send(SseEmitter.event().comment("hb"));
				}
				catch (IOException | IllegalStateException e) {
					remove(code, emitter);
				}
			}
		});
	}

	private void remove(String code, SseEmitter emitter) {
		Set<SseEmitter> set = watchers.get(code);
		if (set == null) {
			return;
		}
		set.remove(emitter);
		if (set.isEmpty()) {
			watchers.remove(code, set);
			log.debug("Plus aucun spectateur pour {}", code);
		}
	}

}
