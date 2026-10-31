package me.raouf.chess.game_service.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "games")
public class Game {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 12)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GameStatus status;

	@Column(name = "current_fen", nullable = false, length = 120)
	private String currentFen;

	@Column(name = "next_move_number", nullable = false)
	private int nextMoveNumber;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "white_user_id")
	private User whiteUser;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "black_user_id")
	private User blackUser;

	@Column(length = 7)
	private String result;

	@Column(name = "end_reason", length = 20)
	private String endReason;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@Column(name = "round")
	private Integer round;

	@Column(name = "initial_ms")
	private Long initialMs;

	@Column(name = "white_remaining_ms")
	private Long whiteRemainingMs;

	@Column(name = "black_remaining_ms")
	private Long blackRemainingMs;

	@Column(name = "clock_deadline")
	private Instant clockDeadline;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "draw_offered_by")
	private User drawOfferedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rematch_game_id")
	private Game rematchGame;

	protected Game() {
	}

	public Tournament getTournament() {
		return tournament;
	}

	public Integer getRound() {
		return round;
	}

	/** Rattache la partie a une ronde de tournoi. */
	public void attachToTournament(Tournament tournament, int round) {
		this.tournament = tournament;
		this.round = round;
	}

	public String getResult() {
		return result;
	}

	public String getEndReason() {
		return endReason;
	}

	/** Termine la partie en enregistrant son resultat (1-0, 0-1 ou 1/2-1/2) et sa raison. */
	public void finish(String result, String endReason) {
		this.status = GameStatus.FINISHED;
		this.result = result;
		this.endReason = endReason;
		this.clockDeadline = null;
		this.drawOfferedBy = null;
	}

	public Long getInitialMs() {
		return initialMs;
	}

	public void setInitialMs(Long initialMs) {
		this.initialMs = initialMs;
	}

	public Long getWhiteRemainingMs() {
		return whiteRemainingMs;
	}

	public void setWhiteRemainingMs(Long whiteRemainingMs) {
		this.whiteRemainingMs = whiteRemainingMs;
	}

	public Long getBlackRemainingMs() {
		return blackRemainingMs;
	}

	public void setBlackRemainingMs(Long blackRemainingMs) {
		this.blackRemainingMs = blackRemainingMs;
	}

	public Instant getClockDeadline() {
		return clockDeadline;
	}

	public void setClockDeadline(Instant clockDeadline) {
		this.clockDeadline = clockDeadline;
	}

	public User getDrawOfferedBy() {
		return drawOfferedBy;
	}

	public void setDrawOfferedBy(User drawOfferedBy) {
		this.drawOfferedBy = drawOfferedBy;
	}

	public Game getRematchGame() {
		return rematchGame;
	}

	public void setRematchGame(Game rematchGame) {
		this.rematchGame = rematchGame;
	}

	public Game(String code, GameStatus status, String currentFen, int nextMoveNumber) {
		this.code = code;
		this.status = status;
		this.currentFen = currentFen;
		this.nextMoveNumber = nextMoveNumber;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public GameStatus getStatus() {
		return status;
	}

	public String getCurrentFen() {
		return currentFen;
	}

	public int getNextMoveNumber() {
		return nextMoveNumber;
	}

	public User getWhiteUser() {
		return whiteUser;
	}

	public User getBlackUser() {
		return blackUser;
	}

	public void setWhiteUser(User whiteUser) {
		this.whiteUser = whiteUser;
	}

	public void setBlackUser(User blackUser) {
		this.blackUser = blackUser;
	}

	/** Couleur qui doit jouer, lue dans le deuxieme champ du FEN. */
	public String getTurn() {
		return currentFen.split(" ")[1].equals("w") ? "WHITE" : "BLACK";
	}

	public void setStatus(GameStatus status) {
		this.status = status;
	}

	public void setCurrentFen(String currentFen) {
		this.currentFen = currentFen;
	}

	public void setNextMoveNumber(int nextMoveNumber) {
		this.nextMoveNumber = nextMoveNumber;
	}

}
