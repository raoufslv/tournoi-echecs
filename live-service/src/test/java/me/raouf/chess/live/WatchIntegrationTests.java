package me.raouf.chess.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@Testcontainers
class WatchIntegrationTests {

	private static final String TOPIC = "chess.game.events";

	@Container
	static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
	}

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private KafkaTemplate<String, String> producer;

	@Autowired
	private WatchRegistry registry;

	/** Ouvre un flux SSE et pousse chaque ligne recue dans une file, depuis un thread de fond. */
	private BlockingQueue<String> open(String code) throws Exception {
		return openPath("/games/" + code + "/watch");
	}

	private BlockingQueue<String> openPath(String path) throws Exception {
		BlockingQueue<String> lines = new LinkedBlockingQueue<>();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Accept", "text/event-stream")
				.build();
		Thread reader = new Thread(() -> {
			try {
				HttpResponse<java.util.stream.Stream<String>> response = HttpClient.newHttpClient()
						.send(request, HttpResponse.BodyHandlers.ofLines());
				response.body().forEach(lines::add);
			}
			catch (Exception e) {
				// connexion fermee a la fin du test
			}
		});
		reader.setDaemon(true);
		reader.start();
		assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo(":connected");
		return lines;
	}

	private static String move(String code, int number, String uci) {
		return "{\"type\":\"move.played\",\"gameId\":1,\"code\":\"" + code + "\",\"moveNumber\":" + number
				+ ",\"uci\":\"" + uci + "\",\"fenAfter\":\"x\",\"playedBy\":\"white\"}";
	}

	/** Lit les lignes jusqu'a obtenir un "data:" ; renvoie [type, json]. */
	private static List<String> nextEvent(BlockingQueue<String> lines) throws Exception {
		String type = null;
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			String line = lines.poll(500, TimeUnit.MILLISECONDS);
			if (line == null) {
				continue;
			}
			if (line.startsWith("event:")) {
				type = line.substring("event:".length()).trim();
			}
			else if (line.startsWith("data:")) {
				return List.of(type, line.substring("data:".length()).trim());
			}
		}
		throw new AssertionError("Aucun evenement SSE recu");
	}

	@Test
	void watcherReceivesEventsOfItsGameInOrder() throws Exception {
		BlockingQueue<String> lines = open("ABC123");
		assertThat(registry.watcherCount("ABC123")).isEqualTo(1);

		producer.send(TOPIC, "1", move("ABC123", 1, "e2e4")).get(10, TimeUnit.SECONDS);
		producer.send(TOPIC, "1", move("ABC123", 2, "e7e5")).get(10, TimeUnit.SECONDS);

		List<String> first = nextEvent(lines);
		List<String> second = nextEvent(lines);

		assertThat(first.get(0)).isEqualTo("move.played");
		assertThat(first.get(1)).contains("\"moveNumber\":1").contains("e2e4");
		assertThat(second.get(1)).contains("\"moveNumber\":2").contains("e7e5");
	}

	@Test
	void watcherDoesNotReceiveEventsOfAnotherGame() throws Exception {
		BlockingQueue<String> watcherA = open("GAMEAA");
		BlockingQueue<String> watcherB = open("GAMEBB");

		producer.send(TOPIC, "2", move("GAMEBB", 1, "d2d4")).get(10, TimeUnit.SECONDS);

		assertThat(nextEvent(watcherB).get(1)).contains("GAMEBB");
		// On laisse a Kafka le temps de livrer : A ne doit rien voir.
		Thread.sleep(1500);
		assertThat(watcherA.stream().filter(l -> l.startsWith("data:"))).isEmpty();
	}

	@Test
	void gameEndedAndSanAreRelayedUnchanged() throws Exception {
		BlockingQueue<String> lines = open("ENDGAM");
		String played = "{\"type\":\"move.played\",\"gameId\":4,\"code\":\"ENDGAM\",\"moveNumber\":1,"
				+ "\"uci\":\"e2e4\",\"san\":\"e4\",\"fenAfter\":\"x\",\"playedBy\":\"white\"}";
		String ended = "{\"type\":\"game.ended\",\"gameId\":4,\"code\":\"ENDGAM\",\"moveNumber\":1,"
				+ "\"result\":\"0-1\",\"endReason\":\"RESIGNATION\",\"finalFen\":\"x\"}";

		producer.send(TOPIC, "4", played).get(10, TimeUnit.SECONDS);
		producer.send(TOPIC, "4", ended).get(10, TimeUnit.SECONDS);

		List<String> first = nextEvent(lines);
		List<String> second = nextEvent(lines);
		assertThat(first.get(0)).isEqualTo("move.played");
		assertThat(first.get(1)).contains("\"san\":\"e4\"");
		assertThat(second.get(0)).isEqualTo("game.ended");
		assertThat(second.get(1)).contains("\"result\":\"0-1\"").contains("\"endReason\":\"RESIGNATION\"");
	}

	@Test
	void tournamentEventsReachOnlyTheTournamentWatchers() throws Exception {
		BlockingQueue<String> tournamentWatcher = openPath("/tournaments/TOURN1/watch");
		// Une partie dont le code est identique a celui du tournoi ne doit rien recevoir.
		BlockingQueue<String> gameWatcher = open("TOURN1");
		String updated = "{\"type\":\"tournament.updated\",\"tournamentId\":9,\"code\":\"TOURN1\","
				+ "\"status\":\"IN_PROGRESS\",\"currentRound\":1}";

		producer.send(TOPIC, "t-9", updated).get(10, TimeUnit.SECONDS);

		List<String> event = nextEvent(tournamentWatcher);
		assertThat(event.get(0)).isEqualTo("tournament.updated");
		assertThat(event.get(1)).contains("\"currentRound\":1");
		Thread.sleep(1500);
		assertThat(gameWatcher.stream().filter(l -> l.startsWith("data:"))).isEmpty();
	}

	@Test
	void gameEventsDoNotReachTournamentWatchers() throws Exception {
		BlockingQueue<String> tournamentWatcher = openPath("/tournaments/SAMECD/watch");
		BlockingQueue<String> gameWatcher = open("SAMECD");

		producer.send(TOPIC, "5", move("SAMECD", 1, "e2e4")).get(10, TimeUnit.SECONDS);

		assertThat(nextEvent(gameWatcher).get(0)).isEqualTo("move.played");
		Thread.sleep(1500);
		assertThat(tournamentWatcher.stream().filter(l -> l.startsWith("data:"))).isEmpty();
	}

	@Test
	void malformedMessagesAreIgnoredAndTheConsumerKeepsWorking() throws Exception {
		BlockingQueue<String> lines = open("OKGAME");

		producer.send(TOPIC, "3", "pas du json").get(10, TimeUnit.SECONDS);
		producer.send(TOPIC, "3", "{\"foo\":1}").get(10, TimeUnit.SECONDS);
		producer.send(TOPIC, "3", move("OKGAME", 1, "g1f3")).get(10, TimeUnit.SECONDS);

		assertThat(nextEvent(lines).get(1)).contains("g1f3");
	}

}
