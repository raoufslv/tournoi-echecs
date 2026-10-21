package me.raouf.chess.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consomme chess.game.events et relaie le message tel quel aux spectateurs de la partie.
 * Il ne valide aucune regle : game-service a deja accepte le coup.
 */
@Component
public class GameEventListener {

	private static final Logger log = LoggerFactory.getLogger(GameEventListener.class);

	private final WatchRegistry registry;
	private final ObjectMapper mapper;

	public GameEventListener(WatchRegistry registry, ObjectMapper mapper) {
		this.registry = registry;
		this.mapper = mapper;
	}

	@KafkaListener(topics = "${app.kafka.topic}")
	public void onMessage(String payload) {
		JsonNode node;
		try {
			node = mapper.readTree(payload);
		}
		catch (Exception e) {
			log.warn("Message ignore, JSON illisible: {}", e.getMessage());
			return;
		}
		String type = node.path("type").asString("");
		String code = node.path("code").asString("");
		if (type.isEmpty() || code.isEmpty()) {
			log.warn("Message ignore, type ou code manquant");
			return;
		}
		// Les evenements de tournoi vont aux spectateurs du tournoi, les autres a ceux de la partie.
		String channel = type.startsWith("tournament.") ? WatchRegistry.tournamentChannel(code) : code;
		int sent = registry.broadcast(channel, type, payload);
		log.debug("{} pour {} envoye a {} spectateur(s)", type, channel, sent);
	}

}
