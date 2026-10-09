package org.sightlesscoders.chess;

import java.util.ArrayList;
import java.util.List;

import org.sightlesscoders.chess.core.Board;
import org.sightlesscoders.chess.core.Move;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the board, pieces, highlights and legal-move markers, and turns taps
 * into move attempts. Drawing happens from an internal snapshot so the UI
 * never reads the board while the AI is searching on another thread.
 */
public class BoardView extends View {

    public interface Listener {
        /** Reason codes for onSquareTapped(). */
        int REASON_SELECTED = 1;   // own piece picked up
        int REASON_INVALID = 2;    // tap that cannot start or complete a move
        int REASON_DESELECT = 3;   // tapped the selected piece again

        /** Called when the player taps a legal destination square. */
        void onMoveRequested(int from, int to);

        /** Called for taps that did not produce a move (used for spoken feedback). */
        void onSquareTapped(int index, int reason, int moveCount);
    }

    // Board colours.
    private static final int COLOR_LIGHT = 0xFFEEEED2;
    private static final int COLOR_DARK = 0xFF769656;
    private static final int COLOR_SELECTED = 0x66F7F669;
    private static final int COLOR_LAST_MOVE = 0x55F5F500;
    private static final int COLOR_CHECK = 0x99E53935;

    private final Board board;
    private Listener listener;

    // Snapshot used for drawing (UI thread only).
    private final char[] displaySq = new char[64];

    private int selected = -1;
    private final List<Integer> legalTargets = new ArrayList<Integer>();
    private int lastMoveFrom = -1;
    private int lastMoveTo = -1;
    private int checkSquare = -1;
    private boolean flipped = false;
    private boolean inputEnabled = true;

    private float cell;
    private float originX;
    private float originY;

    private final Paint squarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint piecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BoardView(Context context, Board board) {
        super(context);
        this.board = board;
        squarePaint.setStyle(Paint.Style.FILL);
        overlayPaint.setStyle(Paint.Style.FILL);
        markerPaint.setStyle(Paint.Style.FILL);
        coordPaint.setTypeface(Typeface.DEFAULT_BOLD);
        coordPaint.setTextAlign(Paint.Align.LEFT);
        piecePaint.setTypeface(Typeface.DEFAULT);
        piecePaint.setTextAlign(Paint.Align.CENTER);
        piecePaint.setStyle(Paint.Style.FILL);
        pieceOutlinePaint.setTypeface(Typeface.DEFAULT);
        pieceOutlinePaint.setTextAlign(Paint.Align.CENTER);
        pieceOutlinePaint.setStyle(Paint.Style.STROKE);
        setBackgroundColor(0xFF1A1A1A);
        refresh();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
        if (!enabled) clearSelection();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void flip() {
        flipped = !flipped;
        clearSelection();
        invalidate();
    }

    public void clearSelection() {
        selected = -1;
        legalTargets.clear();
        invalidate();
    }

    /** Re-read the board state into the drawing snapshot (call setLastMove first). */
    public void refresh() {
        System.arraycopy(board.sq, 0, displaySq, 0, 64);
        checkSquare = -1;
        if (board.inCheck(board.whiteToMove)) {
            char king = board.whiteToMove ? 'K' : 'k';
            for (int i = 0; i < 64; i++) {
                if (displaySq[i] == king) {
                    checkSquare = i;
                    break;
                }
            }
        }
        invalidate();
    }

    /** Set the last-move highlight (call before refresh()). */
    public void setLastMove(Move m) {
        if (m == null) {
            lastMoveFrom = -1;
            lastMoveTo = -1;
        } else {
            lastMoveFrom = m.from;
            lastMoveTo = m.to;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        cell = size / 8f;
        originX = (getWidth() - cell * 8) / 2f;
        originY = (getHeight() - cell * 8) / 2f;

        float half = cell / 2f;
        float textSmall = Math.max(8f, cell * 0.18f);
        float textPiece = cell * 0.78f;
        coordPaint.setTextSize(textSmall);
        piecePaint.setTextSize(textPiece);
        pieceOutlinePaint.setTextSize(textPiece);
        pieceOutlinePaint.setStrokeWidth(Math.max(1f, cell * 0.05f));

        // Squares and highlights.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                float x = originX + c * cell;
                float y = originY + r * cell;
                squarePaint.setColor((r + c) % 2 == 0 ? COLOR_LIGHT : COLOR_DARK);
                canvas.drawRect(x, y, x + cell, y + cell, squarePaint);
                int idx = toIndex(r, c);
                if (idx == lastMoveFrom || idx == lastMoveTo) {
                    overlayPaint.setColor(COLOR_LAST_MOVE);
                    canvas.drawRect(x, y, x + cell, y + cell, overlayPaint);
                }
                if (idx == selected) {
                    overlayPaint.setColor(COLOR_SELECTED);
                    canvas.drawRect(x, y, x + cell, y + cell, overlayPaint);
                }
                if (idx == checkSquare) {
                    overlayPaint.setColor(COLOR_CHECK);
                    canvas.drawRect(x, y, x + cell, y + cell, overlayPaint);
                }
            }
        }

        // Coordinates along the left and bottom edges.
        for (int i = 0; i < 8; i++) {
            // files
            int screenCol = flipped ? 7 - i : i;
            boolean darkFile = (7 + screenCol) % 2 == 1; // bottom row = screen row 7
            coordPaint.setColor(darkFile ? COLOR_LIGHT : COLOR_DARK);
            float fx = originX + screenCol * cell + cell * 0.06f;
            float fy = originY + 7 * cell + cell * 0.06f + textSmall;
            canvas.drawText(String.valueOf((char) ('a' + i)), fx, fy, coordPaint);
            // ranks
            int screenRow = flipped ? 7 - i : i;
            boolean darkRank = (screenRow + 0) % 2 == 1; // left col = screen col 0
            coordPaint.setColor(darkRank ? COLOR_LIGHT : COLOR_DARK);
            float rx = originX + cell * 0.06f;
            float ry = originY + screenRow * cell + cell * 0.06f + textSmall;
            canvas.drawText(String.valueOf((char) ('8' - i)), rx, ry, coordPaint);
        }

        // Pieces (from the snapshot).
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int idx = toIndex(r, c);
                char p = displaySq[idx];
                if (p == 0) continue;
                float cx = originX + c * cell + half;
                float cy = originY + r * cell + half;
                drawPiece(canvas, p, cx, cy);
            }
        }

        // Legal move markers for the selected piece.
        if (selected >= 0) {
            for (int i = 0; i < legalTargets.size(); i++) {
                int idx = legalTargets.get(i);
                int r = idx >> 3, c = idx & 7;
                int sr = flipped ? 7 - r : r;
                int sc = flipped ? 7 - c : c;
                float cx = originX + sc * cell + half;
                float cy = originY + sr * cell + half;
                boolean capture = displaySq[idx] != 0;
                if (capture) {
                    markerPaint.setStyle(Paint.Style.STROKE);
                    markerPaint.setStrokeWidth(cell * 0.09f);
                    markerPaint.setColor(0xB0000000);
                    canvas.drawCircle(cx, cy, cell * 0.42f, markerPaint);
                } else {
                    markerPaint.setStyle(Paint.Style.FILL);
                    markerPaint.setColor(0x90000000);
                    canvas.drawCircle(cx, cy, cell * 0.16f, markerPaint);
                }
            }
        }
    }

    private void drawPiece(Canvas canvas, char p, float cx, float cy) {
        boolean white = Board.isWhite(p);
        int type;
        switch (Character.toLowerCase(p)) {
            case 'k': type = 0; break;
            case 'q': type = 1; break;
            case 'r': type = 2; break;
            case 'b': type = 3; break;
            case 'n': type = 4; break;
            default: type = 5; break;
        }
        char glyph = (char) ((white ? 0x2654 : 0x265A) + type);
        String text = String.valueOf(glyph);
        Paint.FontMetrics fm = piecePaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        // White pieces get a dark outline so they stand out on light squares.
        if (white) {
            pieceOutlinePaint.setColor(0xFF000000);
            canvas.drawText(text, cx, baseline, pieceOutlinePaint);
        }
        piecePaint.setColor(white ? Color.WHITE : 0xFF151515);
        canvas.drawText(text, cx, baseline, piecePaint);
    }

    /** Map a board index to a screen cell (row/col already in screen space). */
    private int toIndex(int screenRow, int screenCol) {
        int r = flipped ? 7 - screenRow : screenRow;
        int c = flipped ? 7 - screenCol : screenCol;
        return r * 8 + c;
    }

    private int cellAt(float x, float y) {
        int c = (int) ((x - originX) / cell);
        int r = (int) ((y - originY) / cell);
        if (r < 0 || r > 7 || c < 0 || c > 7) return -1;
        return toIndex(r, c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (!inputEnabled) return true;
        int idx = cellAt(event.getX(), event.getY());
        if (idx < 0) {
            clearSelection();
            return true;
        }

        if (selected >= 0) {
            if (idx == selected) {
                clearSelection();
                if (listener != null) listener.onSquareTapped(idx, Listener.REASON_DESELECT, 0);
                return true;
            }
            for (int i = 0; i < legalTargets.size(); i++) {
                if (legalTargets.get(i) == idx) {
                    int from = selected;
                    selected = -1;
                    legalTargets.clear();
                    invalidate();
                    if (listener != null) listener.onMoveRequested(from, idx);
                    return true;
                }
            }
        }

        char p = displaySq[idx];
        if (p != 0 && Board.isWhite(p) == board.whiteToMove) {
            selectSquare(idx);
            if (listener != null) {
                listener.onSquareTapped(idx, Listener.REASON_SELECTED, legalTargets.size());
            }
        } else {
            clearSelection();
            if (listener != null) listener.onSquareTapped(idx, Listener.REASON_INVALID, 0);
        }
        return true;
    }

    private void selectSquare(int idx) {
        selected = idx;
        legalTargets.clear();
        List<Move> moves = board.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            if (m.from == idx && !contains(m.to)) legalTargets.add(Integer.valueOf(m.to));
        }
        invalidate();
    }

    private boolean contains(int target) {
        for (int i = 0; i < legalTargets.size(); i++) {
            if (legalTargets.get(i) == target) return true;
        }
        return false;
    }
}
