package me.raouf.chess.game_service.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Appariement suisse leger, sans Spring ni base : testable seul.
 *
 * <p>Les joueurs sont tries par score decroissant (a score egal, l'ordre fourni est conserve : pour la
 * ronde 1, l'appelant les melange). On apparie de haut en bas en evitant les revanches, avec retour
 * arriere quand une impasse apparait. Si aucun appariement sans revanche n'existe, on autorise les
 * revanches plutot que d'echouer. Avec un nombre impair, le bye va au joueur le moins bien classe qui n'en
 * a pas encore eu.</p>
 */
public final class SwissPairing {

	/** colorBalance = parties jouees avec les Blancs moins parties jouees avec les Noirs. */
	public record Player(long id, int halfPoints, boolean hadBye, int colorBalance) {
	}

	public record Pairing(long whiteId, long blackId) {
	}

	/** byeId est null quand le nombre de joueurs est pair. */
	public record Result(List<Pairing> pairings, Long byeId) {
	}

	/** Une rencontre deja jouee, sans tenir compte des couleurs. */
	public record Meeting(long a, long b) {

		public static Meeting of(long x, long y) {
			return new Meeting(Math.min(x, y), Math.max(x, y));
		}

	}

	private SwissPairing() {
	}

	public static Result pair(List<Player> players, Set<Meeting> played) {
		if (players.size() < 2) {
			throw new IllegalArgumentException("Il faut au moins deux joueurs");
		}
		List<Player> sorted = new ArrayList<>(players);
		// Tri stable : l'ordre d'arrivee departage les scores egaux.
		sorted.sort(Comparator.comparingInt(Player::halfPoints).reversed());

		Result strict = tryPair(sorted, played, true);
		if (strict != null) {
			return strict;
		}
		Result relaxed = tryPair(sorted, played, false);
		if (relaxed == null) {
			throw new IllegalStateException("Appariement impossible");
		}
		return relaxed;
	}

	private static Result tryPair(List<Player> sorted, Set<Meeting> played, boolean forbidRematch) {
		if (sorted.size() % 2 == 0) {
			List<Pairing> pairings = new ArrayList<>();
			return match(new ArrayList<>(sorted), played, forbidRematch, pairings) ? new Result(pairings, null) : null;
		}
		// Bye : on essaie d'abord les joueurs du bas du classement qui n'en ont pas eu, puis les autres.
		for (int pass = 0; pass < 2; pass++) {
			boolean needNoBye = pass == 0;
			for (int i = sorted.size() - 1; i >= 0; i--) {
				Player candidate = sorted.get(i);
				if (needNoBye && candidate.hadBye()) {
					continue;
				}
				if (!needNoBye && !candidate.hadBye()) {
					continue;
				}
				List<Player> rest = new ArrayList<>(sorted);
				rest.remove(i);
				List<Pairing> pairings = new ArrayList<>();
				if (match(rest, played, forbidRematch, pairings)) {
					return new Result(pairings, candidate.id());
				}
			}
		}
		return null;
	}

	private static boolean match(List<Player> remaining, Set<Meeting> played, boolean forbidRematch,
			List<Pairing> out) {
		if (remaining.isEmpty()) {
			return true;
		}
		Player first = remaining.get(0);
		for (int i = 1; i < remaining.size(); i++) {
			Player other = remaining.get(i);
			if (forbidRematch && played.contains(Meeting.of(first.id(), other.id()))) {
				continue;
			}
			List<Player> rest = new ArrayList<>(remaining);
			rest.remove(i);
			rest.remove(0);
			out.add(withColors(first, other));
			if (match(rest, played, forbidRematch, out)) {
				return true;
			}
			out.remove(out.size() - 1);
		}
		return false;
	}

	/** Les Blancs vont a celui qui en a le moins eu ; a egalite, au mieux classe des deux. */
	private static Pairing withColors(Player higher, Player lower) {
		if (lower.colorBalance() < higher.colorBalance()) {
			return new Pairing(lower.id(), higher.id());
		}
		return new Pairing(higher.id(), lower.id());
	}

}
