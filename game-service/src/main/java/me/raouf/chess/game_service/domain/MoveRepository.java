package me.raouf.chess.game_service.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MoveRepository extends JpaRepository<Move, Long> {

	long countByGame(Game game);

	List<Move> findByGameOrderByMoveNumberAsc(Game game);

}
