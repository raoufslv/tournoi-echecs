package me.raouf.chess.game_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.api.IllegalMoveException;
import me.raouf.chess.game_service.auth.AuthService;
import me.raouf.chess.game_service.auth.RegisterRequest;
import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.OutboxEvent;
import me.raouf.chess.game_service.domain.OutboxRepository;
import me.raouf.chess.game_service.events.OutboxPublisher;
import me.raouf.chess.game_service.service.GamePlayService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Le publisher planifie est coupe : on appelle publishPending() a la main pour des tests deterministes.
 */
@SpringBootTest(properties = {
		"app.outbox.publisher.enabled=true",
		"app.outbox.publisher.delay-ms=3600000"
})
@Testcontainers
class OutboxTests {

	private static final String TOPIC = "chess.game.events";

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
	}

	@Autowired
	private GamePlayService service;

	@Autowired
	private AuthService auth;

	@Autowired
	private OutboxRepository outbox;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private ObjectMapper mapper;

	private long newUser(String name) {
		return auth.register(new RegisterRequest(
				UUID.randomUUID() + "@test.fr", "motdepasse-solide", name)).getId();
	}

	private Game startedGame(long white, long black) {
		Game game = service.createGame(white);
		service.joinGame(game.getCode(), black);
		return game;
	}

	private List<OutboxEvent> events(Game game) {
		return outbox.findByGameIdOrderByIdAsc(game.getId());
	}

	@Test
	void joinAndMoveWriteOrderedOutboxRows() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);

		service.playMove(game.getCode(), "e2e4", white);
		service.playMove(game.getCode(), "e7e5", black);

		List<OutboxEvent> rows = events(game);
		assertThat(rows).extracting(OutboxEvent::getEventType)
				.containsExactly("player.joined", "move.played", "move.played");
		assertThat(rows).extracting(OutboxEvent::getMoveNumber).containsExactly(null, 1, 2);
		JsonNode first = mapper.readTree(rows.get(1).getPayload());
		assertThat(first.get("uci").asString()).isEqualTo("e2e4");
		assertThat(first.get("playedBy").asString()).isEqualTo("white");
		assertThat(first.get("code").asString()).isEqualTo(game.getCode());
		assertThat(rows).allSatisfy(r -> assertThat(r.getPublishedAt()).isNull());
	}

	@Test
	void rejectedMoveWritesNoOutboxRow() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);
		int before = events(game).size();

		assertThatThrownBy(() -> service.playMove(game.getCode(), "e2e5", white))
				.isInstanceOf(IllegalMoveException.class);
		assertThatThrownBy(() -> service.playMove(game.getCode(), "e7e5", black))
				.isInstanceOf(ApiException.class);

		assertThat(events(game)).hasSize(before);
	}

	@Test
	void checkmateAlsoWritesGameEnded() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);

		service.playMove(game.getCode(), "f2f3", white);
		service.playMove(game.getCode(), "e7e5", black);
		service.playMove(game.getCode(), "g2g4", white);
		service.playMove(game.getCode(), "d8h4", black);

		List<OutboxEvent> rows = events(game);
		OutboxEvent last = rows.get(rows.size() - 1);
		assertThat(last.getEventType()).isEqualTo("game.ended");
		JsonNode payload = mapper.readTree(last.getPayload());
		assertThat(payload.get("result").asString()).isEqualTo("0-1");
		assertThat(payload.get("endReason").asString()).isEqualTo("CHECKMATE");
	}

	@Test
	void moveEventCarriesTheSan() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);

		service.playMove(game.getCode(), "g1f3", white);

		OutboxEvent row = events(game).get(1);
		assertThat(mapper.readTree(row.getPayload()).get("san").asString()).isEqualTo("Nf3");
	}

	@Test
	void resignationWritesGameEndedWithTheReason() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);
		service.playMove(game.getCode(), "e2e4", white);

		service.resign(game.getCode(), black);

		List<OutboxEvent> rows = events(game);
		OutboxEvent last = rows.get(rows.size() - 1);
		assertThat(last.getEventType()).isEqualTo("game.ended");
		JsonNode payload = mapper.readTree(last.getPayload());
		assertThat(payload.get("result").asString()).isEqualTo("1-0");
		assertThat(payload.get("endReason").asString()).isEqualTo("RESIGNATION");
		assertThat(payload.get("moveNumber").asInt()).isEqualTo(1);
	}

	@Test
	void rejectedResignationWritesNothing() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		long stranger = newUser("Intrus");
		Game game = startedGame(white, black);
		int before = events(game).size();

		assertThatThrownBy(() -> service.resign(game.getCode(), stranger)).isInstanceOf(ApiException.class);

		assertThat(events(game)).hasSize(before);
	}

	@Test
	void publisherSendsRowsToKafkaInOrderWithGameIdAsKey() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		Game game = startedGame(white, black);
		service.playMove(game.getCode(), "e2e4", white);
		service.playMove(game.getCode(), "e7e5", black);

		publisher.publishPending();

		assertThat(events(game)).allSatisfy(r -> assertThat(r.getPublishedAt()).isNotNull());

		List<ConsumerRecord<String, String>> received = readTopicFor(game.getCode(), 3);
		assertThat(received).extracting(ConsumerRecord::key)
				.containsOnly(String.valueOf(game.getId()));
		List<String> types = received.stream().map(r -> mapper.readTree(r.value()).get("type").asString()).toList();
		assertThat(types).containsExactly("player.joined", "move.played", "move.played");
		List<Integer> numbers = received.stream()
				.filter(r -> "move.played".equals(mapper.readTree(r.value()).get("type").asString()))
				.map(r -> mapper.readTree(r.value()).get("moveNumber").asInt()).toList();
		assertThat(numbers).containsExactly(1, 2);
	}

	@Test
	void publishedRowsAreNotSentTwice() {
		long white = newUser("Blancs");
		long black = newUser("Noirs");
		startedGame(white, black);
		publisher.publishPending();

		assertThat(publisher.publishPending()).isZero();
	}

	private List<ConsumerRecord<String, String>> readTopicFor(String code, int expected) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		List<ConsumerRecord<String, String>> result = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(TOPIC));
			long deadline = System.currentTimeMillis() + 15_000;
			while (result.size() < expected && System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					if (record.value().contains("\"code\":\"" + code + "\"")) {
						result.add(record);
					}
				}
			}
		}
		return result;
	}

}
