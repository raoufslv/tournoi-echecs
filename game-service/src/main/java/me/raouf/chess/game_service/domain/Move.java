package me.raouf.chess.game_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "moves",
		uniqueConstraints = @UniqueConstraint(
				name = "moves_game_number_unique",
				columnNames = {"game_id", "move_number"}
		)
)
public class Move {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@Column(name = "move_number", nullable = false)
	private int moveNumber;

	@Column(nullable = false, length = 5)
	private String uci;

	@Column(name = "fen_after", nullable = false, length = 120)
	private String fenAfter;

	@Column(length = 12)
	private String san;

	protected Move() {
	}

	public Move(Game game, int moveNumber, String uci, String fenAfter) {
		this(game, moveNumber, uci, null, fenAfter);
	}

	public Move(Game game, int moveNumber, String uci, String san, String fenAfter) {
		this.game = game;
		this.moveNumber = moveNumber;
		this.uci = uci;
		this.san = san;
		this.fenAfter = fenAfter;
	}

	public String getSan() {
		return san;
	}

	public Long getId() {
		return id;
	}

	public Game getGame() {
		return game;
	}

	public int getMoveNumber() {
		return moveNumber;
	}

	public String getUci() {
		return uci;
	}

	public String getFenAfter() {
		return fenAfter;
	}

}
