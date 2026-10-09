package org.sightlesscoders.chess.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Chess board state with complete move generation: legal moves, castling,
 * en passant, promotion, check / checkmate / stalemate detection.
 *
 * Pure Java, no Android dependencies, so it can be unit tested on a desktop JVM.
 * Indexing: index = row * 8 + col, row 0 = rank 8 (top), col 0 = file a.
 */
public class Board {
    public static final int CASTLE_WK = 1; // white king side
    public static final int CASTLE_WQ = 2; // white queen side
    public static final int CASTLE_BK = 4; // black king side
    public static final int CASTLE_BQ = 8; // black queen side

    /** 0 = empty, otherwise piece char ('P','n',...). Uppercase = white. */
    public final char[] sq = new char[64];
    public boolean whiteToMove = true;
    public int castling = CASTLE_WK | CASTLE_WQ | CASTLE_BK | CASTLE_BQ;
    public int ep = -1;      // en passant target square, -1 = none
    public int halfmove = 0; // halfmove clock for the 50-move rule

    private static final char[] PROMOTIONS = { 'Q', 'R', 'B', 'N' };

    private static final int[] KNIGHT_DR = { -2, -2, -1, -1, 1, 1, 2, 2 };
    private static final int[] KNIGHT_DC = { -1, 1, -2, 2, -2, 2, -1, 1 };
    private static final int[] KING_DR = { -1, -1, -1, 0, 0, 1, 1, 1 };
    private static final int[] KING_DC = { -1, 0, 1, -1, 1, -1, 0, 1 };
    private static final int[] ORTHO_DR = { -1, 1, 0, 0 };
    private static final int[] ORTHO_DC = { 0, 0, -1, 1 };
    private static final int[] DIAG_DR = { -1, -1, 1, 1 };
    private static final int[] DIAG_DC = { -1, 1, -1, 1 };

    public Board() {
        reset();
    }

    /** Set up the standard starting position. */
    public void reset() {
        String backRank = "rnbqkbnr";
        for (int i = 0; i < 64; i++) sq[i] = 0;
        for (int c = 0; c < 8; c++) {
            sq[c] = backRank.charAt(c);                 // row 0 = rank 8: black back rank
            sq[8 + c] = 'p';                            // row 1 = rank 7: black pawns
            sq[48 + c] = 'P';                           // row 6 = rank 2: white pawns
            sq[56 + c] = Character.toUpperCase(backRank.charAt(c)); // row 7 = rank 1
        }
        whiteToMove = true;
        castling = CASTLE_WK | CASTLE_WQ | CASTLE_BK | CASTLE_BQ;
        ep = -1;
        halfmove = 0;
    }

    /**
     * Load a position from a FEN string (the first four fields are enough;
     * halfmove/fullmove are read when present).
     */
    public void setFromFen(String fen) {
        for (int i = 0; i < 64; i++) sq[i] = 0;
        String[] parts = fen.trim().split("\\s+");
        int i = 0;
        for (int k = 0; k < parts[0].length(); k++) {
            char ch = parts[0].charAt(k);
            if (ch == '/') continue;
            if (ch >= '1' && ch <= '8') i += ch - '0';
            else if (i < 64) sq[i++] = ch;
        }
        whiteToMove = parts.length > 1 ? !"b".equals(parts[1]) : true;
        castling = 0;
        if (parts.length > 2 && !"-".equals(parts[2])) {
            if (parts[2].indexOf('K') >= 0) castling |= CASTLE_WK;
            if (parts[2].indexOf('Q') >= 0) castling |= CASTLE_WQ;
            if (parts[2].indexOf('k') >= 0) castling |= CASTLE_BK;
            if (parts[2].indexOf('q') >= 0) castling |= CASTLE_BQ;
        }
        ep = -1;
        if (parts.length > 3 && !"-".equals(parts[3])) ep = Move.squareIndex(parts[3]);
        halfmove = parts.length > 4 ? parseInt(parts[4]) : 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isWhite(char p) {
        return p != 0 && Character.isUpperCase(p);
    }

    /** True when the side to move's king is in check. */
    public boolean inCheck() {
        return inCheck(whiteToMove);
    }

    public boolean inCheck(boolean white) {
        char k = white ? 'K' : 'k';
        for (int i = 0; i < 64; i++) {
            if (sq[i] == k) return isAttacked(i, !white);
        }
        return false;
    }

    /** True when square idx is attacked by pieces of the given color. */
    public boolean isAttacked(int idx, boolean byWhite) {
        int r = idx >> 3, c = idx & 7;

        // Pawns: an attacker sits one rank "behind" the attacked square.
        int pr = r + (byWhite ? 1 : -1);
        if (pr >= 0 && pr < 8) {
            char pawn = byWhite ? 'P' : 'p';
            if (c > 0 && sq[pr * 8 + c - 1] == pawn) return true;
            if (c < 7 && sq[pr * 8 + c + 1] == pawn) return true;
        }

        // Knights.
        char knight = byWhite ? 'N' : 'n';
        for (int k = 0; k < 8; k++) {
            int rr = r + KNIGHT_DR[k], cc = c + KNIGHT_DC[k];
            if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && sq[rr * 8 + cc] == knight) return true;
        }

        // Kings.
        char king = byWhite ? 'K' : 'k';
        for (int k = 0; k < 8; k++) {
            int rr = r + KING_DR[k], cc = c + KING_DC[k];
            if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && sq[rr * 8 + cc] == king) return true;
        }

        // Rooks and queens.
        char rook = byWhite ? 'R' : 'r';
        char queen = byWhite ? 'Q' : 'q';
        for (int k = 0; k < 4; k++) {
            int rr = r, cc = c;
            while (true) {
                rr += ORTHO_DR[k];
                cc += ORTHO_DC[k];
                if (rr < 0 || rr > 7 || cc < 0 || cc > 7) break;
                char p = sq[rr * 8 + cc];
                if (p == 0) continue;
                if (p == rook || p == queen) return true;
                break;
            }
        }

        // Bishops and queens.
        char bishop = byWhite ? 'B' : 'b';
        for (int k = 0; k < 4; k++) {
            int rr = r, cc = c;
            while (true) {
                rr += DIAG_DR[k];
                cc += DIAG_DC[k];
                if (rr < 0 || rr > 7 || cc < 0 || cc > 7) break;
                char p = sq[rr * 8 + cc];
                if (p == 0) continue;
                if (p == bishop || p == queen) return true;
                break;
            }
        }
        return false;
    }

    /** All fully legal moves for the side to move. */
    public List<Move> legalMoves() {
        return legalMoves(false);
    }

    /**
     * Legal moves; when capturesOnly is true, only moves that capture a piece
     * are returned (used by the search's quiescence).
     */
    public List<Move> legalMoves(boolean capturesOnly) {
        List<Move> pseudo = pseudoMoves(capturesOnly);
        List<Move> legal = new ArrayList<Move>(pseudo.size());
        boolean moverIsWhite = whiteToMove;
        for (int i = 0; i < pseudo.size(); i++) {
            Move m = pseudo.get(i);
            make(m);
            if (!inCheck(moverIsWhite)) legal.add(m);
            unmake(m);
        }
        return legal;
    }

    /** Pseudo-legal moves for the side to move (may leave the king in check). */
    public List<Move> pseudoMoves(boolean capturesOnly) {
        List<Move> moves = new ArrayList<Move>(64);
        for (int i = 0; i < 64; i++) {
            char p = sq[i];
            if (p == 0) continue;
            boolean white = isWhite(p);
            if (white != whiteToMove) continue;
            switch (Character.toLowerCase(p)) {
                case 'p': genPawn(i, white, moves); break;
                case 'n': genKnight(i, white, moves); break;
                case 'b': genSliding(i, white, DIAG_DR, DIAG_DC, moves); break;
                case 'r': genSliding(i, white, ORTHO_DR, ORTHO_DC, moves); break;
                case 'q':
                    genSliding(i, white, ORTHO_DR, ORTHO_DC, moves);
                    genSliding(i, white, DIAG_DR, DIAG_DC, moves);
                    break;
                case 'k': genKing(i, white, moves); break;
                default: break;
            }
        }
        if (capturesOnly) {
            List<Move> caps = new ArrayList<Move>(moves.size());
            for (int i = 0; i < moves.size(); i++) {
                Move m = moves.get(i);
                if (m.captured != 0) caps.add(m);
            }
            return caps;
        }
        return moves;
    }

    private Move makeMove(int from, int to, char piece, char captured, char promotion,
            boolean enPassant, boolean doublePush, int castle, int rookFrom, int rookTo) {
        return new Move(from, to, piece, captured, promotion, enPassant, doublePush,
                castle, rookFrom, rookTo, castling, ep, halfmove);
    }

    private void genPawn(int i, boolean white, List<Move> moves) {
        int r = i >> 3, c = i & 7;
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;
        int nr = r + dir;
        if (nr < 0 || nr > 7) return;
        char piece = white ? 'P' : 'p';

        // Single (and double) advance.
        int one = nr * 8 + c;
        if (sq[one] == 0) {
            addPawnMove(moves, i, one, piece, (char) 0, false, false, white);
            if (r == startRow) {
                int two = (r + 2 * dir) * 8 + c;
                if (sq[two] == 0) moves.add(makeMove(i, two, piece, (char) 0, (char) 0, false, true, Move.CASTLE_NONE, -1, -1));
            }
        }

        // Captures (including en passant).
        for (int dc = -1; dc <= 1; dc += 2) {
            int nc = c + dc;
            if (nc < 0 || nc > 7) continue;
            int t = nr * 8 + nc;
            char victim = sq[t];
            if (victim != 0) {
                if (isWhite(victim) != white) addPawnMove(moves, i, t, piece, victim, false, false, white);
            } else if (t == ep && ep >= 0) {
                // En passant: the captured pawn sits behind the target square.
                int vicIdx = t + (white ? 8 : -8);
                char vic = sq[vicIdx];
                if (Character.toLowerCase(vic) == 'p' && isWhite(vic) != white) {
                    moves.add(makeMove(i, t, piece, vic, (char) 0, true, false, Move.CASTLE_NONE, -1, -1));
                }
            }
        }
    }

    private void addPawnMove(List<Move> moves, int from, int to, char piece, char captured,
            boolean enPassant, boolean doublePush, boolean white) {
        int r = to >> 3;
        if (r == 0 || r == 7) {
            // Promotion: one move per piece choice.
            for (int i = 0; i < PROMOTIONS.length; i++) {
                moves.add(makeMove(from, to, piece, captured, PROMOTIONS[i], enPassant, doublePush, Move.CASTLE_NONE, -1, -1));
            }
        } else {
            moves.add(makeMove(from, to, piece, captured, (char) 0, enPassant, doublePush, Move.CASTLE_NONE, -1, -1));
        }
        // white is unused beyond readability of the call site
    }

    private void genKnight(int i, boolean white, List<Move> moves) {
        char piece = white ? 'N' : 'n';
        int r = i >> 3, c = i & 7;
        for (int k = 0; k < 8; k++) {
            int rr = r + KNIGHT_DR[k], cc = c + KNIGHT_DC[k];
            if (rr < 0 || rr > 7 || cc < 0 || cc > 7) continue;
            int t = rr * 8 + cc;
            char victim = sq[t];
            if (victim == 0) {
                moves.add(makeMove(i, t, piece, (char) 0, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
            } else if (isWhite(victim) != white) {
                moves.add(makeMove(i, t, piece, victim, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
            }
        }
    }

    private void genSliding(int i, boolean white, int[] dr, int[] dc, List<Move> moves) {
        char piece = sq[i];
        int r = i >> 3, c = i & 7;
        for (int k = 0; k < dr.length; k++) {
            int rr = r + dr[k], cc = c + dc[k];
            while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                int t = rr * 8 + cc;
                char victim = sq[t];
                if (victim == 0) {
                    moves.add(makeMove(i, t, piece, (char) 0, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
                } else {
                    if (isWhite(victim) != white) {
                        moves.add(makeMove(i, t, piece, victim, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
                    }
                    break;
                }
                rr += dr[k];
                cc += dc[k];
            }
        }
    }

    private void genKing(int i, boolean white, List<Move> moves) {
        char piece = white ? 'K' : 'k';
        int r = i >> 3, c = i & 7;
        for (int k = 0; k < 8; k++) {
            int rr = r + KING_DR[k], cc = c + KING_DC[k];
            if (rr < 0 || rr > 7 || cc < 0 || cc > 7) continue;
            int t = rr * 8 + cc;
            char victim = sq[t];
            if (victim == 0) {
                moves.add(makeMove(i, t, piece, (char) 0, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
            } else if (isWhite(victim) != white) {
                moves.add(makeMove(i, t, piece, victim, (char) 0, false, false, Move.CASTLE_NONE, -1, -1));
            }
        }

        // Castling.
        if (white && i == 60) {
            if ((castling & CASTLE_WK) != 0 && sq[61] == 0 && sq[62] == 0 && sq[63] == 'R'
                    && !inCheck(true) && !isAttacked(61, false) && !isAttacked(62, false)) {
                moves.add(makeMove(60, 62, 'K', (char) 0, (char) 0, false, false, Move.CASTLE_KING, 63, 61));
            }
            if ((castling & CASTLE_WQ) != 0 && sq[59] == 0 && sq[58] == 0 && sq[57] == 0 && sq[56] == 'R'
                    && !inCheck(true) && !isAttacked(59, false) && !isAttacked(58, false)) {
                moves.add(makeMove(60, 58, 'K', (char) 0, (char) 0, false, false, Move.CASTLE_QUEEN, 56, 59));
            }
        } else if (!white && i == 4) {
            if ((castling & CASTLE_BK) != 0 && sq[5] == 0 && sq[6] == 0 && sq[7] == 'r'
                    && !inCheck(false) && !isAttacked(5, true) && !isAttacked(6, true)) {
                moves.add(makeMove(4, 6, 'k', (char) 0, (char) 0, false, false, Move.CASTLE_KING, 7, 5));
            }
            if ((castling & CASTLE_BQ) != 0 && sq[3] == 0 && sq[2] == 0 && sq[1] == 0 && sq[0] == 'r'
                    && !inCheck(false) && !isAttacked(3, true) && !isAttacked(2, true)) {
                moves.add(makeMove(4, 2, 'k', (char) 0, (char) 0, false, false, Move.CASTLE_QUEEN, 0, 3));
            }
        }
    }

    /** Apply a move. The same Move must be passed to unmake() to reverse it. */
    public void make(Move m) {
        boolean white = isWhite(m.piece);

        // Move the piece (swap in the promoted piece if needed).
        sq[m.to] = m.promotion != 0
                ? (white ? m.promotion : Character.toLowerCase(m.promotion))
                : m.piece;
        sq[m.from] = 0;

        // Remove an en passant victim (it is not on the target square).
        if (m.enPassant) {
            sq[m.to + (white ? 8 : -8)] = 0;
        }

        // Castle: shift the rook.
        if (m.castle != Move.CASTLE_NONE) {
            sq[m.rookTo] = sq[m.rookFrom];
            sq[m.rookFrom] = 0;
        }

        // Halfmove clock.
        if (Character.toLowerCase(m.piece) == 'p' || m.captured != 0) halfmove = 0;
        else halfmove++;

        // New en passant target (only right after a double pawn push).
        ep = m.doublePush ? m.from + (white ? -8 : 8) : -1;

        // Castling rights.
        if (m.piece == 'K') castling &= ~(CASTLE_WK | CASTLE_WQ);
        else if (m.piece == 'k') castling &= ~(CASTLE_BK | CASTLE_BQ);
        if (m.from == 63 || m.to == 63) castling &= ~CASTLE_WK;
        if (m.from == 56 || m.to == 56) castling &= ~CASTLE_WQ;
        if (m.from == 7 || m.to == 7) castling &= ~CASTLE_BK;
        if (m.from == 0 || m.to == 0) castling &= ~CASTLE_BQ;

        whiteToMove = !whiteToMove;
    }

    /** Reverse a move previously applied with make(). */
    public void unmake(Move m) {
        whiteToMove = !whiteToMove;
        boolean white = isWhite(m.piece);

        sq[m.from] = m.piece;
        sq[m.to] = 0;
        if (m.captured != 0) {
            if (m.enPassant) sq[m.to + (white ? 8 : -8)] = m.captured;
            else sq[m.to] = m.captured;
        }
        if (m.castle != Move.CASTLE_NONE) {
            sq[m.rookFrom] = sq[m.rookTo];
            sq[m.rookTo] = 0;
        }
        castling = m.prevCastling;
        ep = m.prevEp;
        halfmove = m.prevHalfmove;
    }

    /** True when there is nothing left to fight with but the kings (plus one minor). */
    public boolean insufficientMaterial() {
        int minors = 0;
        for (int i = 0; i < 64; i++) {
            char p = sq[i];
            if (p == 0 || Character.toLowerCase(p) == 'k') continue;
            char t = Character.toLowerCase(p);
            if (t == 'b' || t == 'n') minors++;
            else return false; // pawn, rook or queen on the board
        }
        return minors <= 1;
    }

    /** Perft node count used by the move generation tests. */
    public long perft(int depth) {
        if (depth <= 0) return 1;
        List<Move> moves = legalMoves();
        if (depth == 1) return moves.size();
        long nodes = 0;
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            make(m);
            nodes += perft(depth - 1);
            unmake(m);
        }
        return nodes;
    }
}
