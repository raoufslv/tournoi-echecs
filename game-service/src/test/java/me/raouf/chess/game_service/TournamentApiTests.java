package me.raouf.chess.game_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import me.raouf.chess.game_service.domain.OutboxEvent;
import me.raouf.chess.game_service.domain.OutboxRepository;
import me.raouf.chess.game_service.domain.TournamentRepository;

@SpringBootTest(properties = "app.outbox.publisher.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class TournamentApiTests {

	private static final String PASSWORD = "motdepasse-solide";

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TournamentRepository tournaments;

	@Autowired
	private OutboxRepository outbox;

	private record Player(String name, String token) {
	}

	// --- Creation et inscription ---

	@Test
	void creatorBecomesTheOrganizerAndFirstParticipant() throws Exception {
		Player organizer = player("Orga");

		String code = create(organizer, "Soiree du vendredi", 3);

		mvc.perform(get("/tournaments/{code}", code).header(HttpHeaders.AUTHORIZATION, bearer(organizer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Soiree du vendredi"))
				.andExpect(jsonPath("$.status").value("REGISTRATION"))
				.andExpect(jsonPath("$.roundsTotal").value(3))
				.andExpect(jsonPath("$.currentRound").value(0))
				.andExpect(jsonPath("$.organizerName").value(organizer.name()))
				.andExpect(jsonPath("$.standings.length()").value(1));
	}

	@Test
	void invalidCreationRequestsAreRejected() throws Exception {
		Player organizer = player("Orga");

		createRaw(organizer, "{\"name\":\"\",\"rounds\":3}").andExpect(status().isBadRequest());
		createRaw(organizer, "{\"name\":\"Ok\",\"rounds\":2}").andExpect(status().isBadRequest());
		createRaw(organizer, "{\"name\":\"Ok\",\"rounds\":9}").andExpect(status().isBadRequest());
		createRaw(organizer, "{\"name\":\"Ok\"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.roundsTotal").value(3));
	}

	@Test
	void requestWithoutJwtIsUnauthorized() throws Exception {
		mvc.perform(post("/tournaments").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownTournamentIsNotFound() throws Exception {
		Player p = player("Joueur");

		mvc.perform(get("/tournaments/ZZZZZZ").header(HttpHeaders.AUTHORIZATION, bearer(p)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_FOUND"));
		join("ZZZZZZ", p).andExpect(status().isNotFound());
	}

	@Test
	void aPlayerCannotRegisterTwiceAndTheTournamentHasAMaximum() throws Exception {
		Player organizer = player("Orga");
		String code = create(organizer, "Plein", 3);

		join(code, organizer).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

		for (int i = 0; i < 7; i++) {
			join(code, player("J" + i)).andExpect(status().isOk());
		}
		join(code, player("Neuvieme")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_FULL"));
	}

	// --- Lancement ---

	@Test
	void onlyTheOrganizerCanStartAndNeedsEnoughPlayers() throws Exception {
		Player organizer = player("Orga");
		Player friend = player("Ami");
		String code = create(organizer, "Petit", 3);
		join(code, friend).andExpect(status().isOk());

		start(code, friend).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_ORGANIZER"));
		start(code, organizer).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ENOUGH_PLAYERS"));
	}

	@Test
	void startCreatesRoundOneGamesAlreadyInProgress() throws Exception {
		List<Player> players = players(4);
		String code = startedTournament(players, 3);

		String json = tournament(code, players.get(0));
		assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("IN_PROGRESS");
		assertThat(JsonPath.<Integer>read(json, "$.currentRound")).isEqualTo(1);
		List<String> gameCodes = JsonPath.read(json, "$.rounds[0].games[*].gameCode");
		assertThat(gameCodes).hasSize(2);
		assertThat(JsonPath.<List<String>>read(json, "$.rounds[0].games[*].status"))
				.containsOnly("IN_PROGRESS");

		mvc.perform(get("/games/{code}", gameCodes.get(0)).header(HttpHeaders.AUTHORIZATION, bearer(players.get(0))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.tournamentCode").value(code))
				.andExpect(jsonPath("$.round").value(1))
				.andExpect(jsonPath("$.whiteId").isNotEmpty())
				.andExpect(jsonPath("$.blackId").isNotEmpty());
	}

	@Test
	void cannotJoinOrStartOnceStarted() throws Exception {
		List<Player> players = players(4);
		String code = startedTournament(players, 3);

		join(code, player("Retardataire")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_OPEN"));
		start(code, players.get(0)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TOURNAMENT_ALREADY_STARTED"));
	}

	@Test
	void roundsAreLimitedToTheNumberOfOpponents() throws Exception {
		List<Player> players = players(4);
		String code = create(players.get(0), "Trop de rondes", 5);
		for (Player p : players.subList(1, 4)) {
			join(code, p).andExpect(status().isOk());
		}

		start(code, players.get(0)).andExpect(status().isOk())
				.andExpect(jsonPath("$.roundsTotal").value(3));
	}

	// --- Rondes et scores ---

	@Test
	void aFinishedGameUpdatesTheScoreButWaitsForTheRestOfTheRound() throws Exception {
		List<Player> players = players(4);
		String code = startedTournament(players, 3);
		Map<String, Player> byName = byName(players);

		String json = tournament(code, players.get(0));
		String gameCode = JsonPath.<List<String>>read(json, "$.rounds[0].games[*].gameCode").get(0);
		String blackName = JsonPath.<List<String>>read(json, "$.rounds[0].games[*].blackName").get(0);
		String whiteName = JsonPath.<List<String>>read(json, "$.rounds[0].games[*].whiteName").get(0);
		resign(gameCode, byName.get(blackName)).andExpect(status().isOk());

		String after = tournament(code, players.get(0));
		assertThat(JsonPath.<Integer>read(after, "$.currentRound")).isEqualTo(1);
		assertThat(JsonPath.<Integer>read(after, "$.rounds.length()")).isEqualTo(1);
		assertThat(JsonPath.<List<Double>>read(after, "$.standings[?(@.name=='" + whiteName + "')].score"))
				.containsExactly(1.0);
		assertThat(JsonPath.<List<Double>>read(after, "$.standings[?(@.name=='" + blackName + "')].score"))
				.containsExactly(0.0);
	}

	@Test
	void nextRoundStartsByItselfThenTheTournamentFinishes() throws Exception {
		List<Player> players = players(4);
		String code = startedTournament(players, 3);

		Set<Set<String>> meetings = new HashSet<>();
		for (int round = 1; round <= 3; round++) {
			String json = tournament(code, players.get(0));
			assertThat(JsonPath.<Integer>read(json, "$.currentRound")).isEqualTo(round);
			assertThat(JsonPath.<String>read(json, "$.status")).isEqualTo("IN_PROGRESS");
			finishRound(json, round, players, meetings);
		}

		String finished = tournament(code, players.get(0));
		assertThat(JsonPath.<String>read(finished, "$.status")).isEqualTo("FINISHED");
		assertThat(JsonPath.<Integer>read(finished, "$.currentRound")).isEqualTo(3);
		// 4 joueurs, 3 rondes : tout le monde a rencontre tout le monde une seule fois.
		assertThat(meetings).hasSize(6);
		// Chaque partie distribue exactement un point.
		List<Double> scores = JsonPath.read(finished, "$.standings[*].score");
		assertThat(scores.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(6.0);
		assertThat(JsonPath.<Integer>read(finished, "$.standings[0].rank")).isEqualTo(1);
	}

	@Test
	void anOddNumberOfPlayersGivesAOneTimeByeWorthAPoint() throws Exception {
		List<Player> players = players(5);
		String code = startedTournament(players, 3);

		String json = tournament(code, players.get(0));
		assertThat(JsonPath.<List<String>>read(json, "$.rounds[0].games[*].gameCode")).hasSize(2);
		String byeName = JsonPath.read(json, "$.rounds[0].byeName");
		assertThat(byeName).isNotBlank();
		assertThat(JsonPath.<List<Double>>read(json, "$.standings[?(@.name=='" + byeName + "')].score"))
				.containsExactly(1.0);

		// Tout le monde finit les trois rondes : personne n'a deux byes.
		Set<Set<String>> meetings = new HashSet<>();
		Set<String> byes = new HashSet<>();
		for (int round = 1; round <= 3; round++) {
			String current = tournament(code, players.get(0));
			byes.add(JsonPath.read(current, "$.rounds[" + (round - 1) + "].byeName"));
			finishRound(current, round, players, meetings);
		}
		assertThat(byes).hasSize(3);
		assertThat(JsonPath.<String>read(tournament(code, players.get(0)), "$.status")).isEqualTo("FINISHED");
	}

	// --- Outbox ---

	@Test
	void everyChangeWritesATournamentUpdatedEventToTheOutbox() throws Exception {
		List<Player> players = players(4);
		String code = create(players.get(0), "Evenements", 3);
		long id = tournaments.findByCode(code).orElseThrow().getId();
		assertThat(outbox.findByTournamentIdOrderByIdAsc(id)).hasSize(1);

		for (Player p : players.subList(1, 4)) {
			join(code, p).andExpect(status().isOk());
		}
		assertThat(outbox.findByTournamentIdOrderByIdAsc(id)).hasSize(4);

		start(code, players.get(0)).andExpect(status().isOk());
		List<OutboxEvent> events = outbox.findByTournamentIdOrderByIdAsc(id);
		assertThat(events).hasSize(5);
		assertThat(events).allSatisfy(e -> {
			assertThat(e.getEventType()).isEqualTo("tournament.updated");
			assertThat(e.getGameId()).isNull();
			assertThat(e.kafkaKey()).isEqualTo("t-" + id);
		});
		String last = events.get(4).getPayload();
		assertThat(JsonPath.<String>read(last, "$.type")).isEqualTo("tournament.updated");
		assertThat(JsonPath.<String>read(last, "$.code")).isEqualTo(code);
		assertThat(JsonPath.<String>read(last, "$.status")).isEqualTo("IN_PROGRESS");
		assertThat(JsonPath.<Integer>read(last, "$.currentRound")).isEqualTo(1);

		// Une partie terminee previent aussi les spectateurs du tournoi.
		String json = tournament(code, players.get(0));
		String gameCode = JsonPath.<List<String>>read(json, "$.rounds[0].games[*].gameCode").get(0);
		String blackName = JsonPath.<List<String>>read(json, "$.rounds[0].games[*].blackName").get(0);
		resign(gameCode, byName(players).get(blackName)).andExpect(status().isOk());
		assertThat(outbox.findByTournamentIdOrderByIdAsc(id)).hasSize(6);
	}

	// --- Aides ---

	private void finishRound(String json, int round, List<Player> players, Set<Set<String>> meetings)
			throws Exception {
		Map<String, Player> byName = byName(players);
		String base = "$.rounds[" + (round - 1) + "].games[*]";
		List<String> gameCodes = JsonPath.read(json, base + ".gameCode");
		List<String> whites = JsonPath.read(json, base + ".whiteName");
		List<String> blacks = JsonPath.read(json, base + ".blackName");
		for (int i = 0; i < gameCodes.size(); i++) {
			meetings.add(Set.of(whites.get(i), blacks.get(i)));
			resign(gameCodes.get(i), byName.get(blacks.get(i))).andExpect(status().isOk());
		}
	}

	private Map<String, Player> byName(List<Player> players) {
		Map<String, Player> map = new HashMap<>();
		players.forEach(p -> map.put(p.name(), p));
		return map;
	}

	private String startedTournament(List<Player> players, int rounds) throws Exception {
		String code = create(players.get(0), "Tournoi", rounds);
		for (Player p : players.subList(1, players.size())) {
			join(code, p).andExpect(status().isOk());
		}
		start(code, players.get(0)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		return code;
	}

	private String tournament(String code, Player viewer) throws Exception {
		return mvc.perform(get("/tournaments/{code}", code).header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String create(Player organizer, String name, int rounds) throws Exception {
		MvcResult result = createRaw(organizer, "{\"name\":\"" + name + "\",\"rounds\":" + rounds + "}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REGISTRATION"))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
	}

	private ResultActions createRaw(Player organizer, String body) throws Exception {
		return mvc.perform(post("/tournaments")
				.header(HttpHeaders.AUTHORIZATION, bearer(organizer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions join(String code, Player player) throws Exception {
		return mvc.perform(post("/tournaments/{code}/join", code).header(HttpHeaders.AUTHORIZATION, bearer(player)));
	}

	private ResultActions start(String code, Player player) throws Exception {
		return mvc.perform(post("/tournaments/{code}/start", code).header(HttpHeaders.AUTHORIZATION, bearer(player)));
	}

	private ResultActions resign(String gameCode, Player player) throws Exception {
		return mvc.perform(post("/games/{code}/resign", gameCode).header(HttpHeaders.AUTHORIZATION, bearer(player)));
	}

	private List<Player> players(int count) throws Exception {
		List<Player> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(player("P" + i));
		}
		return list;
	}

	/** Cree un compte au nom unique (le nom sert de cle dans les reponses). */
	private Player player(String prefix) throws Exception {
		String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
		String email = UUID.randomUUID() + "@example.com";
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"" + name + "\"}"))
				.andExpect(status().isCreated());
		MvcResult login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return new Player(name, JsonPath.read(login.getResponse().getContentAsString(), "$.token"));
	}

	private static String bearer(Player player) {
		return "Bearer " + player.token();
	}

}
