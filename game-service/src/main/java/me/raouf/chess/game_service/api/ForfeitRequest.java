package me.raouf.chess.game_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForfeitRequest(@NotBlank @Pattern(regexp = "white|black") String color) {
}
