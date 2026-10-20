package me.raouf.chess.game_service.auth;

import me.raouf.chess.game_service.domain.User;

public record UserResponse(Long id, String email, String displayName) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
	}

}
