package me.raouf.chess.game_service.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "game_id", nullable = false)
	private Long gameId;

	@Column(name = "event_type", nullable = false, length = 30)
	private String eventType;

	@Column(nullable = false)
	private String payload;

	@Column(name = "move_number")
	private Integer moveNumber;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
	}

	public OutboxEvent(Long gameId, String eventType, String payload, Integer moveNumber) {
		this.gameId = gameId;
		this.eventType = eventType;
		this.payload = payload;
		this.moveNumber = moveNumber;
	}

	public void markPublished() {
		this.publishedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getGameId() {
		return gameId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public Integer getMoveNumber() {
		return moveNumber;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

}
