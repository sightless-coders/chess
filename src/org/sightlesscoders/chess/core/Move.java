package org.sightlesscoders.chess.core;

/**
 * A single chess move. Carries everything needed to make the move on a
 * Board and later undo it exactly.
 *
 * Board indexing: 0..63 where index = row * 8 + col, row 0 = rank 8 (top of
 * the board as printed), col 0 = file a. So 0 = a8, 7 = h8, 56 = a1, 63 = h1.
 * Pieces are chars: uppercase = white, lowercase = black.
 */
public final class Move {
    public static final int CASTLE_NONE = 0;
    public static final int CASTLE_KING = 1;
    public static final int CASTLE_QUEEN = 2;

    public final int from;
    public final int to;
    public final char piece;        // moving piece before the move ('P','n',...)
    public final char captured;     // captured piece, or 0
    public final char promotion;    // 'Q','R','B','N' when a pawn promotes, else 0
    public final boolean enPassant; // capture was an en passant capture
    public final boolean doublePush;// pawn advanced two squares
    public final int castle;        // CASTLE_KING / CASTLE_QUEEN / CASTLE_NONE
    public final int rookFrom;      // castling: square the rook comes from, else -1
    public final int rookTo;        // castling: square the rook lands on, else -1
    public final int prevCastling;  // castling rights before this move
    public final int prevEp;        // en passant target before this move, -1 = none
    public final int prevHalfmove;  // halfmove clock before this move

    /** Scratch space used when sorting moves for the search. Not part of game state. */
    public int orderScore;

    public Move(int from, int to, char piece, char captured, char promotion,
            boolean enPassant, boolean doublePush, int castle,
            int rookFrom, int rookTo,
            int prevCastling, int prevEp, int prevHalfmove) {
        this.from = from;
        this.to = to;
        this.piece = piece;
        this.captured = captured;
        this.promotion = promotion;
        this.enPassant = enPassant;
        this.doublePush = doublePush;
        this.castle = castle;
        this.rookFrom = rookFrom;
        this.rookTo = rookTo;
        this.prevCastling = prevCastling;
        this.prevEp = prevEp;
        this.prevHalfmove = prevHalfmove;
    }

    public boolean isCapture() {
        return captured != 0;
    }

    /** "e2" style name for a square index. */
    public static String squareName(int idx) {
        if (idx < 0 || idx > 63) return "-";
        return "" + (char) ('a' + (idx & 7)) + (char) ('8' - (idx >> 3));
    }

    /** Inverse of squareName: "e2" -> index, or -1 if the name is invalid. */
    public static int squareIndex(String name) {
        if (name == null || name.length() != 2) return -1;
        int col = name.charAt(0) - 'a';
        int row = '8' - name.charAt(1);
        if (col < 0 || col > 7 || row < 0 || row > 7) return -1;
        return row * 8 + col;
    }

    /**
     * Human readable notation, e.g. "e2-e4", "Ng1-f3", "e4xd5", "e7-e8=Q",
     * "O-O", "e5xe6 e.p.".
     */
    public String notated() {
        if (castle == CASTLE_KING) return "O-O";
        if (castle == CASTLE_QUEEN) return "O-O-O";
        StringBuilder sb = new StringBuilder();
        if (Character.toLowerCase(piece) != 'p') {
            sb.append(Character.toUpperCase(piece));
        }
        sb.append(squareName(from));
        sb.append(captured != 0 ? 'x' : '-');
        sb.append(squareName(to));
        if (enPassant) sb.append(" e.p.");
        if (promotion != 0) sb.append('=').append(promotion);
        return sb.toString();
    }

    @Override
    public String toString() {
        return notated();
    }
}
