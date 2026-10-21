package me.raouf.chess.game_service.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import me.raouf.chess.game_service.domain.OutboxEvent;
import me.raouf.chess.game_service.domain.OutboxRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Ecrit un evenement dans la table outbox. A appeler depuis la transaction qui modifie la partie :
 * MANDATORY empeche d'ecrire un evenement hors transaction.
 */
@Service
public class OutboxService {

	private final OutboxRepository outbox;
	private final ObjectMapper mapper;

	public OutboxService(OutboxRepository outbox, ObjectMapper mapper) {
		this.outbox = outbox;
		this.mapper = mapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(long gameId, String eventType, Integer moveNumber, Object event) {
		outbox.save(new OutboxEvent(gameId, eventType, mapper.writeValueAsString(event), moveNumber));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void recordTournament(long tournamentId, String eventType, Object event) {
		outbox.save(OutboxEvent.forTournament(tournamentId, eventType, mapper.writeValueAsString(event)));
	}

}
