package me.raouf.chess.game_service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.raouf.chess.game_service.api.ApiException;
import me.raouf.chess.game_service.domain.User;
import me.raouf.chess.game_service.domain.UserRepository;

@Service
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final Duration tokenTtl;

	public AuthService(
			UserRepository users,
			PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder,
			@Value("${app.jwt.ttl-minutes:60}") long ttlMinutes) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.tokenTtl = Duration.ofMinutes(ttlMinutes);
	}

	@Transactional
	public User register(RegisterRequest request) {
		String email = normalize(request.email());
		if (users.findByEmail(email).isPresent()) {
			throw ApiException.emailTaken();
		}
		User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().trim());
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException e) {
			throw ApiException.emailTaken();
		}
	}

	@Transactional(readOnly = true)
	public String login(LoginRequest request) {
		User user = users.findByEmail(normalize(request.email()))
				.orElseThrow(ApiException::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw ApiException.invalidCredentials();
		}
		return issueToken(user);
	}

	private String issueToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(tokenTtl))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
