package org.sightlesscoders.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.sightlesscoders.chess.core.Board;
import org.sightlesscoders.chess.core.ChessAI;
import org.sightlesscoders.chess.core.Move;
import org.sightlesscoders.chess.online.AuthRepository;
import org.sightlesscoders.chess.online.LoginActivity;
import org.sightlesscoders.chess.online.StatsRepository;
import org.sightlesscoders.chess.online.UserStats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Chess for blind and sighted players.
 *
 * On every launch the Blind Command audio logo plays and a voice asks
 * "Are you blind or not?":
 *  - blind players double tap (or tap the "I'm blind" button)
 *  - sighted players swipe up with three fingers (or tap "I'm sighted")
 *
 * Blind mode speaks every status change, square tap and dialog through
 * text-to-speech (following Street Fire Arena's Android patterns) and plays
 * audio cues; sighted mode plays the same cues but stays visual.
 */
public class MainActivity extends Activity {

    private static final int MODE_TWO_PLAYERS = 0;
    private static final int MODE_EASY = 1;
    private static final int MODE_NORMAL = 2;
    private static final int MODE_HARD = 3;
    private static final int MODE_ONLINE = 4;

    // The computer plays black.
    private static final boolean AI_IS_BLACK = true;

    private static final String PROMPT =
            "Welcome to Chess. Are you blind or not? If you are blind, double tap. "
            + "If you are not blind, swipe up with three fingers.";

    private Board board;
    private BoardView boardView;
    private TextView statusView;

    // Accessibility / audio.
    private TtsEngine tts;
    private SfxEngine sfx;
    private MediaPlayer logo;
    private boolean talkBack = false;
    private boolean modeChosen = false;
    private boolean blindMode = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int promptRetries = 0;

    // Online
    private AuthRepository authRepository;
    private StatsRepository statsRepository;
    private boolean vsOnline = false;

    // Startup prompt overlay.
    private FrameLayout frame;
    private LinearLayout overlay;
    private TextView instructionsView;
    private final Runnable promptRunnable = new Runnable() {
        @Override
        public void run() {
            speakPrompt();
        }
    };

    private final ChessAI ai = new ChessAI();
    private ExecutorService aiExecutor;
    private boolean aiThinking = false;

    private boolean vsComputer = true;
    private int aiDepth = 3;
    private long aiTimeMs = 1500;

    private boolean gameOver = false;
    private Move lastMove = null;
    private final List<Move> history = new ArrayList<Move>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        AccessibilityManager am =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        talkBack = am != null && am.isTouchExplorationEnabled();

        authRepository = new AuthRepository(this);
        statsRepository = new StatsRepository(authRepository);

        board = new Board();
        buildUi();

        tts = new TtsEngine(this);
        sfx = new SfxEngine(this);

        newGame(MODE_NORMAL);
        playLogo();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopLogo();
        if (aiExecutor != null) aiExecutor.shutdownNow();
        if (sfx != null) sfx.release();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI ---

    private void buildUi() {
        int pad = dp(12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(18f);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(android.view.Gravity.CENTER);
        statusView.setMinLines(2);
        statusView.setPadding(pad, pad, pad, pad / 2);
        statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(statusView, wrapParams());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(pad, 0, pad, pad / 2);
        buttons.addView(makeButton("New game", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModeDialog();
            }
        }));
        buttons.addView(makeButton("Undo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undo();
            }
        }));
        buttons.addView(makeButton("Flip", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boardView.flip();
            }
        }));
        buttons.addView(makeButton("Stats", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStatsDialog();
            }
        }));
        if (authRepository != null && authRepository.isLoggedIn()) {
            buttons.addView(makeButton("Sign out", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signOut();
                }
            }));
        }
        root.addView(buttons, wrapParams());

        boardView = new BoardView(this, board);
        boardView.setListener(new BoardView.Listener() {
            @Override
            public void onMoveRequested(int from, int to) {
                tryMove(from, to);
            }

            @Override
            public void onSquareTapped(int index, int reason, int moveCount) {
                squareTapped(index, reason, moveCount);
            }
        });
        root.addView(boardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        buildPromptOverlay();
        frame.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(frame);

        // Keep content out of the status/navigation bars (edge to edge on Android 15+).
        getWindow().getDecorView().setOnApplyWindowInsetsListener(
                new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        frame.setPadding(insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                        return insets;
                    }
                });
    }

    /**
     * Full-screen startup screen: plays host to the "are you blind?" voice and
     * recognises both gestures. It sits on top of the game until a mode is
     * chosen, so no stray taps reach the board.
     */
    private void buildPromptOverlay() {
        int pad = dp(24);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(android.view.Gravity.CENTER);
        overlay.setBackgroundColor(0xF2101418);
        overlay.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Chess");
        title.setTextColor(Color.WHITE);
        title.setTextSize(36f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        overlay.addView(title, wrapParams());

        instructionsView = new TextView(this);
        instructionsView.setText("Are you blind?\n\n"
                + "If you are blind, double tap anywhere.\n\n"
                + "If you are not blind, swipe up with 3 fingers.");
        instructionsView.setTextColor(0xFFDDDDDD);
        instructionsView.setTextSize(20f);
        instructionsView.setGravity(android.view.Gravity.CENTER);
        instructionsView.setPadding(0, dp(24), 0, dp(36));
        overlay.addView(instructionsView, wrapParams());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button blindButton = new Button(this);
        blindButton.setText("I'm blind");
        blindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(true);
            }
        });
        blindButton.setOnTouchListener(labelSpeaker("I'm blind"));
        Button sightedButton = new Button(this);
        sightedButton.setText("I'm sighted");
        sightedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(false);
            }
        });
        sightedButton.setOnTouchListener(labelSpeaker("I'm sighted"));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams sightedLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(blindButton, half);
        row.addView(sightedButton, sightedLp);
        overlay.addView(row, wrapParams());

        overlay.setOnTouchListener(promptGestureListener);
    }

    private final View.OnTouchListener promptGestureListener = new View.OnTouchListener() {
        private long lastTapTime;
        private float lastTapX;
        private float lastTapY;
        private float downX;
        private float downY;
        private float threeStartY = -1;
        private float threeStartX;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (modeChosen) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    threeStartY = -1;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() >= 3) {
                        threeStartY = averageY(e);
                        threeStartX = averageX(e);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() >= 3 && threeStartY >= 0) {
                        float dy = threeStartY - averageY(e);
                        float dx = Math.abs(averageX(e) - threeStartX);
                        if (dy > 80 && dy > dx) {
                            chooseMode(false); // sighted: swipe up with 3 fingers
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    threeStartY = -1;
                    break;

                case MotionEvent.ACTION_UP: {
                    long now = SystemClock.uptimeMillis();
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    if (dx * dx + dy * dy < 40 * 40) { // a tap, not a drag
                        boolean isDouble = now - lastTapTime < 350
                                && Math.abs(e.getX() - lastTapX) < 60
                                && Math.abs(e.getY() - lastTapY) < 60;
                        if (isDouble) {
                            handler.removeCallbacks(promptRunnable);
                            chooseMode(true); // blind: double tap
                            return true;
                        }
                        lastTapTime = now;
                        lastTapX = e.getX();
                        lastTapY = e.getY();
                        // A single tap repeats the question if it was missed.
                        handler.removeCallbacks(promptRunnable);
                        handler.postDelayed(promptRunnable, 400);
                    }
                    break;
                }
                default:
                    break;
            }
            return true;
        }

        private float averageY(MotionEvent e) {
            float sum = 0;
            for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
            return sum / e.getPointerCount();
        }

        private float averageX(MotionEvent e) {
            float sum = 0;
            for (int i = 0; i < e.getPointerCount(); i++) sum += e.getX(i);
            return sum / e.getPointerCount();
        }
    };

    // ------------------------------------------------------- startup logo ---

    private void playLogo() {
        try {
            AssetFileDescriptor afd = getAssets().openFd("sounds/logo.ogg");
            logo = new MediaPlayer();
            logo.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            logo.prepare();
            logo.start();
        } catch (Exception e) {
            stopLogo();
        }
        // The voice question follows just after the logo starts.
        handler.removeCallbacks(promptRunnable);
        handler.postDelayed(promptRunnable, 1200);
    }

    private void stopLogo() {
        if (logo != null) {
            try {
                logo.stop();
            } catch (Exception ignored) {
            }
            logo.release();
            logo = null;
        }
    }

    /** Speak (or re-speak) the accessibility question while it is showing. */
    private void speakPrompt() {
        if (modeChosen || tts == null) return;
        if (!tts.isReady()) {
            // TTS engine still initialising; retry for a few seconds.
            if (promptRetries++ < 40) handler.postDelayed(promptRunnable, 500);
            return;
        }
        if (!tts.isSpeaking()) {
            tts.speak(PROMPT, true);
        }
    }

    /** Blind / sighted chosen. Dismisses the prompt and confirms by voice. */
    private void chooseMode(boolean blind) {
        if (modeChosen) return;
        modeChosen = true;
        blindMode = blind;
        handler.removeCallbacks(promptRunnable);
        if (tts != null) tts.stop();
        stopLogo();
        if (sfx != null) sfx.play("ui/click");
        if (frame != null && overlay != null) {
            frame.removeView(overlay);
            overlay = null;
        }
        boardView.setInputEnabled(!gameOver
                && (!vsComputer || board.whiteToMove != AI_IS_BLACK));
        String status = vsComputer
                ? "New game. You play White against the computer. Your move."
                : "New game. Two players. White to move.";
        statusView.setText(status);
        if (blind) {
            // The game speaks for itself now; keep the screen reader quiet.
            statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
            if (tts != null) {
                tts.speak((blind ? "Blind mode. " : "Sighted mode. ") + status, true);
            }
        }
    }

    // ---------------------------------------------------------- feedback ---

    /**
     * Speak text through the game's own TTS voice (Street Fire Arena style):
     * the prompt phase and blind mode always use it, sighted mode stays
     * silent after the startup question.
     */
    private void feedback(String text) {
        if (text == null || text.isEmpty() || tts == null) return;
        if (!modeChosen) {
            tts.speak(text, false); // label while the prompt is up
        } else if (blindMode) {
            tts.speak(text, true);
        }
    }

    private void setStatus(String text) {
        statusView.setText(text);
        if (modeChosen && blindMode && tts != null) {
            tts.speak(text, true);
        }
    }

    /** Called by BoardView for taps that did not produce a move. */
    private void squareTapped(int index, int reason, int moveCount) {
        String desc = squareDescription(index);
        if (reason == BoardView.Listener.REASON_SELECTED) {
            if (sfx != null) sfx.play("ui/click");
            feedback(moveCount == 1 ? desc + ", 1 move."
                    : desc + ", " + moveCount + " moves.");
        } else if (reason == BoardView.Listener.REASON_INVALID) {
            if (sfx != null) sfx.play("ui/error");
            feedback(desc);
        } else {
            feedback(desc);
        }
    }

    private String squareDescription(int idx) {
        char p = board.sq[idx];
        if (p == 0) return Move.squareName(idx) + ", empty";
        return Move.squareName(idx) + ", " + pieceWords(p);
    }

    private static String pieceWords(char p) {
        String color = Board.isWhite(p) ? "white" : "black";
        switch (Character.toLowerCase(p)) {
            case 'k': return color + " king";
            case 'q': return color + " queen";
            case 'r': return color + " rook";
            case 'b': return color + " bishop";
            case 'n': return color + " knight";
            default: return color + " pawn";
        }
    }

    private View.OnTouchListener labelSpeaker(final String label) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) feedback(label);
                return false;
            }
        };
    }

    private static LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private Button makeButton(String label, View.OnClickListener handler) {
        Button b = new Button(this, null, android.R.attr.buttonStyleSmall);
        b.setText(label);
        b.setOnClickListener(handler);
        b.setOnTouchListener(labelSpeaker(label));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------- GAMES ----

    private void newGame(int mode) {
        if (aiThinking) return; // the engine is busy; don't yank the board away
        vsComputer = mode != MODE_TWO_PLAYERS && mode != MODE_ONLINE;
        vsOnline = mode == MODE_ONLINE;
        switch (mode) {
            case MODE_EASY:   aiDepth = 1; aiTimeMs = 500;  break;
            case MODE_HARD:   aiDepth = 4; aiTimeMs = 3000; break;
            default:          aiDepth = 3; aiTimeMs = 1500; break;
        }
        board.reset();
        history.clear();
        lastMove = null;
        gameOver = false;
        boardView.setLastMove(null);
        boardView.clearSelection();
        boardView.refresh();
        boardView.setInputEnabled(modeChosen);

        if (vsComputer) {
            setStatus("New game — you play White against the computer. Your move.");
        } else if (vsOnline) {
            // Online game started via matchmaking
            setStatus("Online game — waiting for opponent...");
        } else {
            setStatus("New game — two players. White to move.");
        }
    }

    private void showModeDialog() {
        if (aiThinking) {
            setStatus("The computer is still thinking — try again in a moment.");
            return;
        }
        final String[] choices = {
                "Computer — Easy",
                "Computer — Normal",
                "Computer — Hard",
                "Two players (local)",
                "Online matchmaking",
        };
        final int[] modes = { MODE_EASY, MODE_NORMAL, MODE_HARD, MODE_TWO_PLAYERS, MODE_ONLINE };
        new AlertDialog.Builder(this)
                .setTitle("New game")
                .setItems(choices, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (modes[which] == MODE_ONLINE) {
                            startOnlineMatchmaking();
                        } else {
                            newGame(modes[which]);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        feedback("New game. Choose opponent: computer easy, normal, hard, two players, or online.");
    }

    /** Player tapped a destination square; resolve which move (if any) that was. */
    private void tryMove(int from, int to) {
        if (gameOver || aiThinking || !modeChosen) return;
        List<Move> options = new ArrayList<Move>();
        for (Move m : board.legalMoves()) {
            if (m.from == from && m.to == to) options.add(m);
        }
        if (options.isEmpty()) return;
        if (options.size() == 1) {
            applyMove(options.get(0));
            return;
        }
        // Promotion: let the player pick the piece.
        final List<Move> promotions = options;
        final char[] wanted = { 'Q', 'R', 'B', 'N' };
        new AlertDialog.Builder(this)
                .setTitle("Promote to")
                .setItems(new String[] { "Queen", "Rook", "Bishop", "Knight" },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for (Move m : promotions) {
                                    if (m.promotion == wanted[which]) {
                                        applyMove(m);
                                        return;
                                    }
                                }
                            }
                        })
                .show();
        feedback("Promote to: queen, rook, bishop, or knight?");
    }

    private void applyMove(Move m) {
        boolean moverWasWhite = Board.isWhite(m.piece);
        String mover = moverWasWhite ? "White" : "Black";

        board.make(m);
        history.add(m);
        lastMove = m;
        boardView.setLastMove(m);
        boardView.clearSelection();
        if (sfx != null) sfx.play("ui/move");

        String status;
        List<Move> legal = board.legalMoves();
        if (legal.isEmpty()) {
            gameOver = true;
            if (board.inCheck()) {
                String winner = moverWasWhite ? "Black" : "White";
                status = m.notated() + ". Checkmate! " + winner
                        + " wins. Tap New game to play again.";
            } else {
                status = m.notated() + ". Stalemate — draw. Tap New game to play again.";
            }
        } else if (board.insufficientMaterial()) {
            gameOver = true;
            status = m.notated() + ". Draw — insufficient material.";
        } else if (board.halfmove >= 100) {
            gameOver = true;
            status = m.notated() + ". Draw — 50-move rule.";
        } else {
            String side = board.whiteToMove ? "White" : "Black";
            if (board.inCheck()) {
                status = m.notated() + ". Check — " + side + " to move.";
            } else {
                status = m.notated() + ". " + side + " to move.";
            }
            if (vsComputer && (board.whiteToMove != AI_IS_BLACK)) {
                status = "Your move. " + status;
            }
        }
        setStatus(status);
        boardView.refresh();
        boardView.setInputEnabled(!gameOver && modeChosen
                && (!vsComputer || board.whiteToMove != AI_IS_BLACK));
        if (!gameOver) maybeStartAiTurn();
    }

    private boolean isAiTurn() {
        return vsComputer && board.whiteToMove != AI_IS_BLACK;
    }

    private void maybeStartAiTurn() {
        if (gameOver || !isAiTurn() || aiThinking) return;
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("The computer is thinking…");
        if (aiExecutor == null) aiExecutor = Executors.newSingleThreadExecutor();
        final int depth = aiDepth;
        final long timeMs = aiTimeMs;
        aiExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Move move = ai.findBestMove(board, depth, timeMs);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aiThinking = false;
                        if (isFinishing() || isDestroyed()) return;
                        if (move != null) {
                            applyMove(move);
                        }
                    }
                });
            }
        });
    }

    private void undo() {
        if (aiThinking) {
            setStatus("The computer is still thinking — try again in a moment.");
            return;
        }
        if (vsOnline) {
            setStatus("Undo not available in online games.");
            return;
        }
        if (history.isEmpty()) {
            setStatus("Nothing to undo.");
            return;
        }
        // In computer mode take back the computer's move and yours, so it is
        // your turn again.
        int guard = 0;
        while (!history.isEmpty() && guard++ < 8) {
            board.unmake(history.remove(history.size() - 1));
            if (vsComputer && board.whiteToMove != AI_IS_BLACK) continue;
            break;
        }
        gameOver = false;
        lastMove = history.isEmpty() ? null : history.get(history.size() - 1);
        boardView.setLastMove(lastMove);
        boardView.clearSelection();
        boardView.refresh();
        boardView.setInputEnabled(!gameOver && modeChosen
                && (!vsComputer || board.whiteToMove != AI_IS_BLACK));
        String side = board.whiteToMove ? "White" : "Black";
        setStatus("Move taken back. " + side + " to move.");
    }

    private void startOnlineMatchmaking() {
        if (!authRepository.isLoggedIn()) {
            setStatus("Please sign in to play online.");
            return;
        }
        setStatus("Finding opponent...");

        org.sightlesscoders.chess.online.MatchmakingRepository matchmaking =
                new org.sightlesscoders.chess.online.MatchmakingRepository(authRepository);
        matchmaking.findMatch(new org.sightlesscoders.chess.online.MatchmakingRepository.MatchCallback() {
            @Override
            public void onMatched(String gameId, boolean isWhite) {
                runOnUiThread(() -> {
                    vsOnline = true;
                    vsComputer = false;
                    board.reset();
                    history.clear();
                    lastMove = null;
                    gameOver = false;
                    boardView.setLastMove(null);
                    boardView.clearSelection();
                    boardView.refresh();
                    boardView.setInputEnabled(modeChosen && isWhite);
                    String status = isWhite
                            ? "Online game — you are White. Your move."
                            : "Online game — you are Black. Waiting for opponent.";
                    setStatus(status);
                    // TODO: Listen for opponent moves via Firebase
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> setStatus("Matchmaking failed: " + e.getMessage()));
            }
        });
    }

    private void showStatsDialog() {
        if (!authRepository.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle("Statistics")
                    .setMessage("Sign in to view your statistics.")
                    .setPositiveButton("Sign In", (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        statsRepository.getStats(new StatsRepository.StatsCallback() {
            @Override
            public void onSuccess(UserStats stats) {
                runOnUiThread(() -> {
                    String msg = String.format(
                            "Wins: %d\nLosses: %d\nDraws: %d\nTotal: %d\nWin Rate: %.1f%%",
                            stats.wins, stats.losses, stats.draws, stats.totalGames, stats.getWinRate()
                    );
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Your Statistics")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() ->
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Error")
                                .setMessage("Failed to load stats: " + e.getMessage())
                                .setPositiveButton("OK", null)
                                .show()
                );
            }
        });
    }

    private void signOut() {
        authRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
