package me.raouf.chess.game_service.service;

import java.util.List;

import me.raouf.chess.game_service.domain.Game;
import me.raouf.chess.game_service.domain.Tournament;
import me.raouf.chess.game_service.domain.TournamentParticipant;

/** Tout ce qu'il faut pour afficher un tournoi : inscrits et parties, dans l'ordre. */
public record TournamentDetails(Tournament tournament, List<TournamentParticipant> participants, List<Game> games) {
}
