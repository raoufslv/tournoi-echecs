package me.raouf.chess.game_service.service;

import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;

/** Notation algebrique standard (e4, Nf3, exd5, O-O, e8=Q, Qh4#) d'un coup dans une position donnee. */
final class SanNotation {

	private SanNotation() {
	}

	static String of(String fenBefore, Move move) {
		MoveList list = new MoveList(fenBefore);
		list.add(move);
		return list.toSanArray()[0];
	}

}
