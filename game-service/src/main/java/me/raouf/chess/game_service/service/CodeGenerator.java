package me.raouf.chess.game_service.service;

import java.security.SecureRandom;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/** Codes courts et partageables, sans caracteres ambigus (0/O, 1/I). */
@Component
public class CodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;

	private final SecureRandom random = new SecureRandom();

	/** Tire des codes jusqu'a en trouver un qui n'existe pas deja. */
	public String next(Predicate<String> alreadyUsed) {
		String code = random();
		while (alreadyUsed.test(code)) {
			code = random();
		}
		return code;
	}

	private String random() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
