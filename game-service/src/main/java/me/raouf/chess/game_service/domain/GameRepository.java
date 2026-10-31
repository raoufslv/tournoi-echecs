package me.raouf.chess.game_service.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Long> {

	Optional<Game> findByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Game> findWithLockByCode(String code);

	List<Game> findByTournamentOrderByRoundAscIdAsc(Tournament tournament);

	List<Game> findByWhiteUser_IdOrBlackUser_IdOrderByIdDesc(Long whiteUserId, Long blackUserId);

	@Query("""
			select g.code from Game g
			where g.status = :status
			and g.clockDeadline is not null
			and g.clockDeadline < :now
			""")
	List<String> findCodesPastDeadline(GameStatus status, Instant now);

}
