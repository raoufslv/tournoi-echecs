package me.raouf.chess.game_service.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** rounds est optionnel (3 par defaut) ; il sera ramene a "joueurs - 1" au lancement si besoin. */
public record CreateTournamentRequest(
		@NotBlank @Size(max = 60) String name,
		@Min(3) @Max(5) Integer rounds
) {
}
