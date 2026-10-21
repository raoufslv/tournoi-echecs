package me.raouf.chess.game_service.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tournament_participants")
public class TournamentParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "tournament_id", nullable = false)
	private Tournament tournament;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** Score en demi-points : victoire = 2, nulle = 1. */
	@Column(name = "half_points", nullable = false)
	private int halfPoints;

	@Column(name = "bye_round")
	private Integer byeRound;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt = Instant.now();

	protected TournamentParticipant() {
	}

	public TournamentParticipant(Tournament tournament, User user) {
		this.tournament = tournament;
		this.user = user;
	}

	public Long getId() {
		return id;
	}

	public Tournament getTournament() {
		return tournament;
	}

	public User getUser() {
		return user;
	}

	public int getHalfPoints() {
		return halfPoints;
	}

	public Integer getByeRound() {
		return byeRound;
	}

	public boolean hadBye() {
		return byeRound != null;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public void addHalfPoints(int points) {
		this.halfPoints += points;
	}

	/** Un bye vaut une victoire (2 demi-points) et ne peut arriver qu'une fois. */
	public void giveBye(int round) {
		this.byeRound = round;
		this.halfPoints += 2;
	}

}
