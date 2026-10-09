package org.sightlesscoders.chess.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Negamax search with alpha-beta pruning, capture-only quiescence,
 * piece-square evaluation and iterative deepening under a time budget.
 * Pure Java, no Android dependencies.
 */
public class ChessAI {
    private static final int MATE = 100000;
    private static final int INF = 10000000;
    private static final int MAX_QUIESCENCE_PLY = 5;

    private long deadline;
    private boolean aborted;
    private long nodes;

    // Material values (centipawns), indexed from the white point of view.
    private static int baseValue(char p) {
        switch (Character.toLowerCase(p)) {
            case 'p': return 100;
            case 'n': return 320;
            case 'b': return 330;
            case 'r': return 500;
            case 'q': return 900;
            case 'k': return 0;
            default: return 0;
        }
    }

    // Piece-square tables, white's point of view, row 0 = rank 8.
    private static final int[][] PST = {
        { // pawn
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5, -5,-10,  0,  0,-10, -5,  5,
             5, 10, 10,-20,-20, 10, 10,  5,
             0,  0,  0,  0,  0,  0,  0,  0
        },
        { // knight
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50
        },
        { // bishop
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5, 10, 10,  5,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
        },
        { // rook
              0,  0,  0,  0,  0,  0,  0,  0,
              5, 10, 10, 10, 10, 10, 10,  5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
              0,  0,  0,  5,  5,  0,  0,  0
        },
        { // queen
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
             -5,  0,  5,  5,  5,  5,  0, -5,
              0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20
        },
        { // king (middlegame)
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
             20, 20,  0,  0,  0,  0, 20, 20,
             20, 30, 10,  0,  0, 10, 30, 20
        }
    };

    private static int typeIndex(char p) {
        switch (Character.toLowerCase(p)) {
            case 'p': return 0;
            case 'n': return 1;
            case 'b': return 2;
            case 'r': return 3;
            case 'q': return 4;
            default: return 5;
        }
    }

    /** Static evaluation in centipawns, positive = good for white. */
    public static int evaluate(Board b) {
        int score = 0;
        int whiteBishops = 0, blackBishops = 0;
        for (int i = 0; i < 64; i++) {
            char p = b.sq[i];
            if (p == 0) continue;
            boolean white = Board.isWhite(p);
            int t = typeIndex(p);
            int idx = white ? i : (i ^ 56); // mirror the table for black
            int v = baseValue(p) + PST[t][idx];
            if (white) score += v;
            else score -= v;
            if (t == 2) {
                if (white) whiteBishops++;
                else blackBishops++;
            }
        }
        if (whiteBishops >= 2) score += 30; // bishop pair
        if (blackBishops >= 2) score -= 30;
        return score;
    }

    /**
     * Pick a move for the side to move. Searches up to maxDepth plies but
     * never spends longer than timeLimitMs. Returns null when no legal move
     * exists (checkmate or stalemate).
     */
    public Move findBestMove(Board board, int maxDepth, long timeLimitMs) {
        deadline = System.currentTimeMillis() + timeLimitMs;
        aborted = false;
        nodes = 0;

        List<Move> rootMoves = board.legalMoves();
        if (rootMoves.isEmpty()) return null;
        Move best = rootMoves.get(0);

        for (int depth = 1; depth <= maxDepth; depth++) {
            Move iterationBest = searchRoot(board, rootMoves, depth);
            if (aborted) break;
            if (iterationBest != null) {
                best = iterationBest;
                // Try the winner first on the next iteration.
                rootMoves.remove(best);
                rootMoves.add(0, best);
            }
            if (System.currentTimeMillis() >= deadline) break;
        }
        return best;
    }

    private Move searchRoot(Board board, List<Move> moves, int depth) {
        order(moves);
        Move best = null;
        int alpha = -INF;
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            board.make(m);
            int score = -alphaBeta(board, depth - 1, -INF, -alpha, 1);
            board.unmake(m);
            if (aborted) return null;
            if (best == null || score > alpha) {
                alpha = score;
                best = m;
            }
        }
        return best;
    }

    private int alphaBeta(Board board, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.currentTimeMillis() >= deadline) {
            aborted = true;
            return 0;
        }
        if (aborted) return 0;
        if (board.halfmove >= 100) return 0; // 50-move rule
        if (depth <= 0) return quiescence(board, alpha, beta, ply, 0);

        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) {
            return board.inCheck() ? -(MATE - ply) : 0;
        }
        order(moves);
        int best = -INF;
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            board.make(m);
            int score = -alphaBeta(board, depth - 1, -beta, -alpha, ply + 1);
            board.unmake(m);
            if (aborted) return 0;
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break; // beta cutoff
        }
        return best;
    }

    /** Resolve captures at the horizon so the search doesn't miss tactics. */
    private int quiescence(Board board, int alpha, int beta, int ply, int qply) {
        if ((++nodes & 1023) == 0 && System.currentTimeMillis() >= deadline) {
            aborted = true;
            return 0;
        }
        if (aborted) return 0;

        int sideScore = board.whiteToMove ? evaluate(board) : -evaluate(board);
        if (sideScore >= beta) return beta;
        if (sideScore > alpha) alpha = sideScore;
        if (qply >= MAX_QUIESCENCE_PLY) return alpha;

        List<Move> captures = board.legalMoves(true);
        order(captures);
        for (int i = 0; i < captures.size(); i++) {
            Move m = captures.get(i);
            board.make(m);
            int score = -quiescence(board, -beta, -alpha, ply + 1, qply + 1);
            board.unmake(m);
            if (aborted) return 0;
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private static int orderScore(Move m) {
        int s = 0;
        if (m.captured != 0) s += 1000 + 10 * baseValue(m.captured) - baseValue(m.piece);
        if (m.promotion != 0) s += 800 + baseValue(m.promotion);
        if (m.castle != Move.CASTLE_NONE) s += 50;
        return s;
    }

    private static void order(List<Move> moves) {
        for (int i = 0; i < moves.size(); i++) {
            moves.get(i).orderScore = orderScore(moves.get(i));
        }
        Collections.sort(moves, new Comparator<Move>() {
            @Override
            public int compare(Move a, Move b) {
                return b.orderScore - a.orderScore;
            }
        });
    }
}
