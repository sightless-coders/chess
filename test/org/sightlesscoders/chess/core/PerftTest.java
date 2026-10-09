package org.sightlesscoders.chess.core;

import java.util.List;

/**
 * Desktop test harness (no Android): validates the move generator against
 * known perft counts and checks game-ending detection plus a quick AI sanity
 * pass. Run with test.bat. Exits non-zero on any failure.
 */
public class PerftTest {
    private static int failures = 0;

    public static void main(String[] args) {
        // --- Perft: known node counts from the chess programming wiki ---
        perft("start position",
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                new int[] { 20, 400, 8902, 197281 });
        perft("kiwipete (castling, pins)",
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                new int[] { 48, 2039, 97862 });
        perft("en passant position",
                "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                new int[] { 14, 191, 2812, 43238 });
        perft("promotion position",
                "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                new int[] { 6, 264, 9467 });
        perft("midgame position",
                "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                new int[] { 44, 1486, 62379 });

        // --- Checkmate detection: Fool's mate position, black delivered mate ---
        Board mate = new Board();
        mate.setFromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        List<Move> mateMoves = mate.legalMoves();
        check("checkmate: no legal moves", mateMoves.isEmpty());
        check("checkmate: white is in check", mate.inCheck(true));

        // --- Stalemate detection ---
        Board stale = new Board();
        stale.setFromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        List<Move> staleMoves = stale.legalMoves();
        check("stalemate: no legal moves", staleMoves.isEmpty());
        check("stalemate: black not in check", !stale.inCheck(false));

        // --- Undo must restore the exact position ---
        Board undo = new Board();
        String before = dump(undo);
        List<Move> first = undo.legalMoves();
        for (int i = 0; i < first.size(); i++) {
            Move m = first.get(i);
            undo.make(m);
            undo.unmake(m);
            check("undo restores position for " + m.notated(), before.equals(dump(undo)));
        }

        // --- AI: returns a legal move quickly ---
        Board ai = new Board();
        ChessAI engine = new ChessAI();
        long t0 = System.currentTimeMillis();
        Move aiMove = engine.findBestMove(ai, 3, 2000);
        long elapsed = System.currentTimeMillis() - t0;
        check("AI returned a move", aiMove != null);
        if (aiMove != null) {
            List<Move> legal = ai.legalMoves();
            boolean found = false;
            for (Move m : legal) {
                if (m.from == aiMove.from && m.to == aiMove.to && m.promotion == aiMove.promotion) {
                    found = true;
                    break;
                }
            }
            check("AI move is legal (" + aiMove.notated() + ")", found);
        }
        System.out.println("AI move in " + elapsed + " ms: " + aiMove);

        // --- AI finds mate in 1 ---
        Board mateInOne = new Board();
        mateInOne.setFromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"); // no mate here, just a position
        Move tactical = new ChessAI().findBestMove(mateInOne, 3, 3000);
        check("AI returns a move from any position", tactical != null);

        Board mateIn1 = new Board();
        mateIn1.setFromFen("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4");
        Move finisher = new ChessAI().findBestMove(mateIn1, 3, 3000);
        boolean mates = false;
        if (finisher != null) {
            mateIn1.make(finisher);
            mates = mateIn1.legalMoves().isEmpty() && mateIn1.inCheck(false);
        }
        check("AI finds mate in 1 (scholar's mate, got: "
                + (finisher == null ? "none" : finisher.notated()) + ")", mates);

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println(failures + " TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static void perft(String name, String fen, int[] expected) {
        Board b = new Board();
        b.setFromFen(fen);
        for (int depth = 1; depth <= expected.length; depth++) {
            long t0 = System.currentTimeMillis();
            long nodes = b.perft(depth);
            long ms = System.currentTimeMillis() - t0;
            String label = "perft " + name + " depth " + depth;
            if (nodes == expected[depth - 1]) {
                System.out.println("PASS  " + label + ": " + nodes + " nodes (" + ms + " ms)");
            } else {
                System.out.println("FAIL  " + label + ": got " + nodes + ", expected " + expected[depth - 1]);
                failures++;
            }
        }
    }

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + label);
        if (!ok) failures++;
    }

    private static String dump(Board b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) sb.append(b.sq[i] == 0 ? '.' : b.sq[i]);
        sb.append(' ').append(b.whiteToMove ? 'w' : 'b');
        sb.append(' ').append(b.castling);
        sb.append(' ').append(b.ep);
        sb.append(' ').append(b.halfmove);
        return sb.toString();
    }
}
