package me.raouf.chess.game_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlayMoveRequest(
		@NotBlank
		@Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbnQRBN]?$")
		String uci
) {
}
