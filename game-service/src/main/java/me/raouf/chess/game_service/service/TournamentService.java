package me.raouf.chess.game_service.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.github.bhlangonijr.chesslib.Board;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.GameRepository;
import me.raouf.chess.game_service.domain.GameStatus;
import me.raouf.chess.game_service.domain.Tournament;
import me.raouf.chess.game_service.domain.TournamentParticipant;
import me.raouf.chess.game_service.domain.TournamentParticipantRepository;
import me.raouf.chess.game_service.domain.TournamentRepository;
import me.raouf.chess.game_service.domain.TournamentStatus;
import me.raouf.chess.game_service.domain.User;
import me.raouf.chess.game_service.domain.UserRepository;
import me.raouf.chess.game_service.events.OutboxService;
import me.raouf.chess.game_service.events.TournamentUpdatedEvent;
import me.raouf.chess.game_service.service.SwissPairing.Meeting;
import me.raouf.chess.game_service.service.SwissPairing.Pairing;
import me.raouf.chess.game_service.service.SwissPairing.Player;

/**
 * Tournoi suisse leger : inscription, lancement, rondes automatiques et scores.
 * Tout ce qui modifie le tournoi se fait sous verrou pessimiste sur la ligne du tournoi.
 */
@Service
public class TournamentService {

	public static final int MIN_PLAYERS = 4;
	public static final int MAX_PLAYERS = 8;
	public static final int DEFAULT_ROUNDS = 3;

	private final TournamentRepository tournaments;
	private final TournamentParticipantRepository participants;
	private final GameRepository games;
	private final UserRepository users;
	private final OutboxService outbox;
	private final CodeGenerator codes;
	private final EntityManager entityManager;
	private final Random random = new SecureRandom();

	public TournamentService(TournamentRepository tournaments, TournamentParticipantRepository participants,
			GameRepository games, UserRepository users, OutboxService outbox, CodeGenerator codes,
			EntityManager entityManager) {
		this.tournaments = tournaments;
		this.participants = participants;
		this.games = games;
		this.users = users;
		this.outbox = outbox;
		this.codes = codes;
		this.entityManager = entityManager;
	}

	/** Cree le tournoi ; l'organisateur en est aussi le premier inscrit. */
	@Transactional
	public Tournament create(long userId, String name, Integer rounds) {
		User organizer = findUser(userId);
		String code = codes.next(candidate -> tournaments.findByCode(candidate).isPresent());
		Tournament tournament = tournaments.save(
				new Tournament(code, name.trim(), organizer, rounds == null ? DEFAULT_ROUNDS : rounds));
		participants.save(new TournamentParticipant(tournament, organizer));
		recordUpdate(tournament);
		return tournament;
	}

	@Transactional
	public Tournament join(String code, long userId) {
		Tournament tournament = lock(code);
		if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
			throw ApiException.tournamentNotOpen();
		}
		User user = findUser(userId);
		if (participants.existsByTournamentAndUser(tournament, user)) {
			throw ApiException.alreadyRegistered();
		}
		if (participants.countByTournament(tournament) >= MAX_PLAYERS) {
			throw ApiException.tournamentFull();
		}
		participants.save(new TournamentParticipant(tournament, user));
		recordUpdate(tournament);
		return tournament;
	}

	@Transactional
	public Tournament start(String code, long userId) {
		Tournament tournament = lock(code);
		if (!tournament.getCreatedBy().getId().equals(userId)) {
			throw ApiException.notOrganizer();
		}
		if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
			throw ApiException.tournamentAlreadyStarted();
		}
		List<TournamentParticipant> registered = participants.findByTournamentOrderByIdAsc(tournament);
		if (registered.size() < MIN_PLAYERS) {
			throw ApiException.notEnoughPlayers();
		}
		// Au plus une rencontre contre chacun : pas plus de rondes que d'adversaires.
		tournament.setRoundsTotal(Math.min(tournament.getRoundsTotal(), registered.size() - 1));
		tournament.setStatus(TournamentStatus.IN_PROGRESS);
		startRound(tournament, 1, registered);
		recordUpdate(tournament);
		return tournament;
	}

	@Transactional(readOnly = true)
	public TournamentDetails get(String code) {
		Tournament tournament = tournaments.findByCode(code).orElseThrow(ApiException::tournamentNotFound);
		return new TournamentDetails(tournament, participants.findByTournamentOrderByIdAsc(tournament),
				games.findByTournamentOrderByRoundAscIdAsc(tournament));
	}

	/**
	 * Appelee a la fin de chaque partie (dans sa transaction) : met a jour les scores, puis lance la ronde
	 * suivante ou cloture le tournoi quand toutes les parties de la ronde sont terminees.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void onGameFinished(Game game) {
		Tournament tournament = game.getTournament();
		if (tournament == null) {
			return;
		}
		// refresh + verrou : l'instance chargee avec la partie peut etre perimee si une autre partie
		// du tournoi s'est terminee entre-temps.
		entityManager.refresh(tournament, LockModeType.PESSIMISTIC_WRITE);
		if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
			return;
		}

		List<TournamentParticipant> all = participants.findByTournamentOrderByIdAsc(tournament);
		applyResult(game, all);

		boolean roundComplete = games.findByTournamentOrderByRoundAscIdAsc(tournament).stream()
				.filter(g -> g.getRound() != null && g.getRound() == tournament.getCurrentRound())
				.allMatch(g -> g.getStatus() == GameStatus.FINISHED);
		if (roundComplete) {
			if (tournament.getCurrentRound() >= tournament.getRoundsTotal()) {
				tournament.setStatus(TournamentStatus.FINISHED);
			}
			else {
				startRound(tournament, tournament.getCurrentRound() + 1, all);
			}
		}
		recordUpdate(tournament);
	}

	private void applyResult(Game game, List<TournamentParticipant> all) {
		Map<Long, TournamentParticipant> byUser = new HashMap<>();
		all.forEach(p -> byUser.put(p.getUser().getId(), p));
		TournamentParticipant white = byUser.get(game.getWhiteUser().getId());
		TournamentParticipant black = byUser.get(game.getBlackUser().getId());
		switch (game.getResult()) {
			case "1-0" -> white.addHalfPoints(2);
			case "0-1" -> black.addHalfPoints(2);
			default -> {
				white.addHalfPoints(1);
				black.addHalfPoints(1);
			}
		}
	}

	private void startRound(Tournament tournament, int round, List<TournamentParticipant> registered) {
		List<Game> played = games.findByTournamentOrderByRoundAscIdAsc(tournament);
		Set<Meeting> meetings = new HashSet<>();
		Map<Long, Integer> colorBalance = new HashMap<>();
		for (Game game : played) {
			long white = game.getWhiteUser().getId();
			long black = game.getBlackUser().getId();
			meetings.add(Meeting.of(white, black));
			colorBalance.merge(white, 1, Integer::sum);
			colorBalance.merge(black, -1, Integer::sum);
		}

		List<TournamentParticipant> ordered = new ArrayList<>(registered);
		if (round == 1) {
			Collections.shuffle(ordered, random);
		}
		List<Player> players = ordered.stream()
				.map(p -> new Player(p.getUser().getId(), p.getHalfPoints(), p.hadBye(),
						colorBalance.getOrDefault(p.getUser().getId(), 0)))
				.toList();
		SwissPairing.Result result = SwissPairing.pair(players, meetings);

		Map<Long, User> usersById = new HashMap<>();
		registered.forEach(p -> usersById.put(p.getUser().getId(), p.getUser()));
		String startFen = new Board().getFen();
		for (Pairing pairing : result.pairings()) {
			String code = codes.next(candidate -> games.findByCode(candidate).isPresent());
			Game game = new Game(code, GameStatus.IN_PROGRESS, startFen, 1);
			game.setWhiteUser(usersById.get(pairing.whiteId()));
			game.setBlackUser(usersById.get(pairing.blackId()));
			game.attachToTournament(tournament, round);
			games.save(game);
		}
		if (result.byeId() != null) {
			registered.stream().filter(p -> p.getUser().getId().equals(result.byeId())).findFirst()
					.orElseThrow().giveBye(round);
		}
		tournament.setCurrentRound(round);
	}

	private Tournament lock(String code) {
		return tournaments.findWithLockByCode(code).orElseThrow(ApiException::tournamentNotFound);
	}

	private User findUser(long userId) {
		return users.findById(userId).orElseThrow(ApiException::invalidCredentials);
	}

	private void recordUpdate(Tournament tournament) {
		outbox.recordTournament(tournament.getId(), TournamentUpdatedEvent.TYPE,
				TournamentUpdatedEvent.of(tournament.getId(), tournament.getCode(), tournament.getStatus().name(),
						tournament.getCurrentRound()));
	}

}
