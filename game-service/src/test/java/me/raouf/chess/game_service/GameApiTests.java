package me.raouf.chess.game_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.GameRepository;
import me.raouf.chess.game_service.domain.GameStatus;
import me.raouf.chess.game_service.domain.Move;
import me.raouf.chess.game_service.domain.MoveRepository;

@SpringBootTest(properties = "app.outbox.publisher.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class GameApiTests {

	private static final String PASSWORD = "motdepasse-solide";

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private GameRepository games;

	@Autowired
	private MoveRepository moves;

	// --- Comptes et JWT ---

	@Test
	void registerThenLoginReturnsAToken() throws Exception {
		String email = uniqueEmail();

		mvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email, "Alice")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.displayName").value("Alice"));

		mvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void duplicateEmailIsRejected() throws Exception {
		String email = uniqueEmail();
		register(email, "Alice");

		mvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email, "Autre")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		String email = uniqueEmail();
		register(email, "Alice");

		mvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, "pas-le-bon-mot-de-passe")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void requestWithoutJwtIsUnauthorized() throws Exception {
		mvc.perform(post("/games")).andExpect(status().isUnauthorized());
	}

	// --- Rejoindre une partie ---

	@Test
	void thirdPlayerCannotJoinAFullGame() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String third = tokenFor("Troisieme");
		String code = createGame(white);

		join(code, black).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.whiteName").value("Blancs"))
				.andExpect(jsonPath("$.blackName").value("Noirs"));

		join(code, third).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_FULL"));
	}

	@Test
	void creatorCannotJoinTheirOwnGame() throws Exception {
		String white = tokenFor("Blancs");
		String code = createGame(white);

		join(code, white).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_FULL"));
	}

	// --- Coups ---

	@Test
	void moveOnAWaitingGameIsRejected() throws Exception {
		String white = tokenFor("Blancs");
		String code = createGame(white);

		move(code, white, "e2e4").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_NOT_STARTED"));
	}

	@Test
	void blackCannotPlayFirstThenPlaysAfterWhite() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);

		move(code, black, "e7e5").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_YOUR_TURN"));

		move(code, white, "e2e4").andExpect(status().isOk());

		move(code, black, "e7e5").andExpect(status().isOk());
		move(code, black, "d7d5").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_YOUR_TURN"));
	}

	@Test
	void corsPreflightIsAllowedForTheFrontendOrigin() throws Exception {
		mvc.perform(options("/games")
				.header(HttpHeaders.ORIGIN, "http://localhost:4200")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
	}

	@Test
	void gameResponseExposesPlayerIds() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);

		mvc.perform(get("/games/" + code).header(HttpHeaders.AUTHORIZATION, "Bearer " + white))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.whiteId").isNumber())
				.andExpect(jsonPath("$.blackId").isNumber());
	}

	@Test
	void spectatorCannotPlay() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String spectator = tokenFor("Spectateur");
		String code = startedGame(white, black);

		move(code, spectator, "e2e4").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_YOUR_TURN"));
	}

	@Test
	void e2e4ChangesTheFenAndStoresMoveNumberOne() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);
		String fenBefore = games.findByCode(code).orElseThrow().getCurrentFen();

		move(code, white, "e2e4")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fen").value("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"))
				.andExpect(jsonPath("$.nextMoveNumber").value(2))
				.andExpect(jsonPath("$.turn").value("BLACK"));

		Game game = games.findByCode(code).orElseThrow();
		assertThat(game.getCurrentFen()).isNotEqualTo(fenBefore);
		assertThat(moves.countByGame(game)).isEqualTo(1);
	}

	@Test
	void illegalMoveLeavesTheGameUnchanged() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);
		String fenBefore = games.findByCode(code).orElseThrow().getCurrentFen();

		move(code, white, "e2e5")
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("ILLEGAL_MOVE"));

		Game game = games.findByCode(code).orElseThrow();
		assertThat(game.getCurrentFen()).isEqualTo(fenBefore);
		assertThat(moves.countByGame(game)).isZero();
	}

	// --- Historique, abandon, resultat ---

	@Test
	void movesEndpointReturnsTheOrderedHistoryWithSan() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);
		move(code, white, "e2e4").andExpect(status().isOk());
		move(code, black, "e7e5").andExpect(status().isOk());
		move(code, white, "g1f3").andExpect(status().isOk());

		mvc.perform(get("/games/{code}/moves", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + white))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].moveNumber").value(1))
				.andExpect(jsonPath("$[0].san").value("e4"))
				.andExpect(jsonPath("$[1].san").value("e5"))
				.andExpect(jsonPath("$[2].san").value("Nf3"))
				.andExpect(jsonPath("$[2].uci").value("g1f3"))
				.andExpect(jsonPath("$[2].fenAfter").isString());
	}

	@Test
	void movesEndpointReturns404ForAnUnknownGame() throws Exception {
		String token = tokenFor("Blancs");
		mvc.perform(get("/games/ZZZZZZ/moves").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void blackResigningGivesWhiteTheWin() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);

		resign(code, black).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FINISHED"))
				.andExpect(jsonPath("$.result").value("1-0"))
				.andExpect(jsonPath("$.endReason").value("RESIGNATION"));
		move(code, white, "e2e4").andExpect(status().isUnprocessableEntity());
	}

	@Test
	void whiteResigningGivesBlackTheWin() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);

		resign(code, white).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("0-1"));
	}

	@Test
	void spectatorCannotResign() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String spectator = tokenFor("Spectateur");
		String code = startedGame(white, black);

		resign(code, spectator).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SPECTATOR_CANNOT_MOVE"));
	}

	@Test
	void cannotResignAGameThatHasNotStarted() throws Exception {
		String white = tokenFor("Blancs");
		String code = createGame(white);

		resign(code, white).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_NOT_STARTED"));
	}

	@Test
	void cannotResignAFinishedGame() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);
		resign(code, white).andExpect(status().isOk());

		resign(code, black).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GAME_ALREADY_FINISHED"));
	}

	@Test
	void checkmateStoresTheResult() throws Exception {
		String white = tokenFor("Blancs");
		String black = tokenFor("Noirs");
		String code = startedGame(white, black);

		move(code, white, "f2f3").andExpect(status().isOk());
		move(code, black, "e7e5").andExpect(status().isOk());
		move(code, white, "g2g4").andExpect(status().isOk());
		move(code, black, "d8h4").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FINISHED"))
				.andExpect(jsonPath("$.result").value("0-1"))
				.andExpect(jsonPath("$.endReason").value("CHECKMATE"));

		mvc.perform(get("/games/{code}", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + white))
				.andExpect(jsonPath("$.result").value("0-1"))
				.andExpect(jsonPath("$.endReason").value("CHECKMATE"));
		mvc.perform(get("/games/{code}/moves", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + white))
				.andExpect(jsonPath("$[3].san").value("Qh4#"));
	}

	@Test
	void duplicateMoveNumberIsRejectedByTheDatabase() {
		Game game = games.save(new Game("DUP123", GameStatus.IN_PROGRESS, "fen", 1));
		moves.saveAndFlush(new Move(game, 1, "e2e4", "fen-after"));

		assertThatThrownBy(() -> moves.saveAndFlush(new Move(game, 1, "e7e5", "fen-other")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Aides ---

	private String uniqueEmail() {
		return UUID.randomUUID() + "@example.com";
	}

	private String registerBody(String email, String displayName) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"" + displayName + "\"}";
	}

	private String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private void register(String email, String displayName) throws Exception {
		mvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email, displayName)))
				.andExpect(status().isCreated());
	}

	private String tokenFor(String displayName) throws Exception {
		String email = uniqueEmail();
		register(email, displayName);
		MvcResult result = mvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
	}

	private String createGame(String token) throws Exception {
		MvcResult result = mvc.perform(post("/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
	}

	private String startedGame(String whiteToken, String blackToken) throws Exception {
		String code = createGame(whiteToken);
		join(code, blackToken).andExpect(status().isOk());
		return code;
	}

	private org.springframework.test.web.servlet.ResultActions join(String code, String token) throws Exception {
		return mvc.perform(post("/games/{code}/join", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private org.springframework.test.web.servlet.ResultActions resign(String code, String token) throws Exception {
		return mvc.perform(post("/games/{code}/resign", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private org.springframework.test.web.servlet.ResultActions move(String code, String token, String uci)
			throws Exception {
		return mvc.perform(post("/games/{code}/moves", code)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"uci\":\"" + uci + "\"}"));
	}

}
