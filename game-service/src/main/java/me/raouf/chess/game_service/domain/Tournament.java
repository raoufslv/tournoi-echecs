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
@Table(name = "tournaments")
public class Tournament {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 12)
	private String code;

	@Column(nullable = false, length = 60)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TournamentStatus status;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "rounds_total", nullable = false)
	private int roundsTotal;

	@Column(name = "current_round", nullable = false)
	private int currentRound;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected Tournament() {
	}

	public Tournament(String code, String name, User createdBy, int roundsTotal) {
		this.code = code;
		this.name = name;
		this.createdBy = createdBy;
		this.roundsTotal = roundsTotal;
		this.status = TournamentStatus.REGISTRATION;
		this.currentRound = 0;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public TournamentStatus getStatus() {
		return status;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public int getRoundsTotal() {
		return roundsTotal;
	}

	public int getCurrentRound() {
		return currentRound;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setStatus(TournamentStatus status) {
		this.status = status;
	}

	public void setRoundsTotal(int roundsTotal) {
		this.roundsTotal = roundsTotal;
	}

	public void setCurrentRound(int currentRound) {
		this.currentRound = currentRound;
	}

}
