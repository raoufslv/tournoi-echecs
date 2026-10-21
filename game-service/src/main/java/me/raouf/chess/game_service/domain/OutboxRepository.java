package me.raouf.chess.game_service.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();

	long countByPublishedAtIsNull();

	List<OutboxEvent> findByGameIdOrderByIdAsc(Long gameId);

	List<OutboxEvent> findByTournamentIdOrderByIdAsc(Long tournamentId);

}
