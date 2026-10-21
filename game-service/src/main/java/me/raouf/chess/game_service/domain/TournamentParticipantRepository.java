package me.raouf.chess.game_service.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {

	List<TournamentParticipant> findByTournamentOrderByIdAsc(Tournament tournament);

	Optional<TournamentParticipant> findByTournamentAndUser(Tournament tournament, User user);

	boolean existsByTournamentAndUser(Tournament tournament, User user);

	long countByTournament(Tournament tournament);

}
