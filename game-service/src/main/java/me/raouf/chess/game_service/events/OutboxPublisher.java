package me.raouf.chess.game_service.events;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import me.raouf.chess.game_service.domain.OutboxEvent;
import me.raouf.chess.game_service.domain.OutboxRepository;

/**
 * Envoie vers Kafka les evenements de l'outbox pas encore acquittes, dans l'ordre des ids.
 * Si Kafka est indisponible, la ligne reste non publiee et sera retentee au prochain passage.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
	private static final long SEND_TIMEOUT_SECONDS = 10;

	private final OutboxRepository outbox;
	private final KafkaTemplate<String, String> kafka;
	private final String topic;

	public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
			@Value("${app.kafka.topic}") String topic) {
		this.outbox = outbox;
		this.kafka = kafka;
		this.topic = topic;
	}

	@Scheduled(fixedDelayString = "${app.outbox.publisher.delay-ms:500}")
	public void scheduledPublish() {
		publishPending();
	}

	/** Publie un lot ; renvoie le nombre d'evenements acquittes par Kafka. */
	public int publishPending() {
		List<OutboxEvent> batch = outbox.findTop50ByPublishedAtIsNullOrderByIdAsc();
		int published = 0;
		for (OutboxEvent event : batch) {
			try {
				kafka.send(topic, event.kafkaKey(), event.getPayload())
						.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return published;
			}
			catch (Exception e) {
				// On s'arrete au premier echec pour ne jamais publier un coup avant le precedent.
				log.warn("Echec de publication outbox id={} ({}), {} evenement(s) en attente",
						event.getId(), e.getClass().getSimpleName(), outbox.countByPublishedAtIsNull());
				return published;
			}
			event.markPublished();
			outbox.save(event);
			published++;
		}
		if (published > 0) {
			log.debug("{} evenement(s) publie(s) sur {}", published, topic);
		}
		return published;
	}

}
