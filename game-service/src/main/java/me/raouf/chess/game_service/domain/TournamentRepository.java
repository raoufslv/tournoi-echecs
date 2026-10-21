package me.raouf.chess.game_service.domain;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

	Optional<Tournament> findByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Tournament> findWithLockByCode(String code);

}
