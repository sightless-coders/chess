# Chess (Android, Java)

A complete, self-contained chess game for Android written in plain Java — no
Gradle, no dependencies. Build it with a single double-clicked `.bat` file.

## Features

- **Accessible for blind and sighted players.** On every launch the Blind
  Command audio logo plays and a voice asks *"Are you blind or not?"*:
  - **blind** → double tap anywhere (or tap *I'm blind*)
  - **sighted** → swipe up with 3 fingers (or tap *I'm sighted*)

  In blind mode every status change, square tap and dialog is spoken through
  text-to-speech ("e2, white pawn, 2 moves", "Check — Black to move"), button
  labels are read on touch, and click/move/error audio cues play (same sounds
  and TTS pattern as Street Fire Arena's Android port, in `assets/sounds/`).
  Sighted mode plays the same audio cues but stays visual. The game always
  speaks with **its own TTS voice** (Street Fire Arena style): in blind mode
  the screen reader's status announcements are suppressed so there is only
  one voice — the game's.
- Full legal chess rules: castling, en passant, promotion (you pick the piece),
  check / checkmate / stalemate, the 50-move rule and draws for insufficient
  material.
- Built-in computer opponent: negamax search with alpha-beta pruning,
  quiescence search, piece-square evaluation and iterative deepening under a
  time budget. Three difficulties (Easy / Normal / Hard).
- Two-player hot-seat mode on one screen, with a board flip button.
- Tap a piece, tap a destination; legal moves are marked (dot = quiet move,
  ring = capture), last move and check are highlighted.
- TalkBack friendly: every move, check and game result is announced through
  the status line's accessibility live region.

## Layout

```
AndroidManifest.xml      app manifest (portrait, launcher activity)
build.bat                compile + sign the APK
test.bat                 run the engine test suite on the desktop JVM
install.bat              adb install + launch
src/org/sightlesscoders/chess/          Android UI (MainActivity, BoardView,
                                        TtsEngine, SfxEngine)
src/org/sightlesscoders/chess/core/     pure-Java engine (Board, Move, ChessAI)
test/                                   perft / rules / AI tests
assets/sounds/           audio logo (Blind_Command.ogg) + UI sound cues
res/drawable/            vector launcher icon
build/                   build output (chess.apk ends up here)
```

The `core` package has **no Android imports**, so the rules engine is tested
directly on any desktop JVM.

## Requirements

- **Android SDK** with a platform and build-tools (installed with Android
  Studio; `build.bat` finds it via `ANDROID_HOME` or
  `%LOCALAPPDATA%\Android\Sdk`).
- **JDK 11 or newer** for `javac`, `d8` and `apksigner`. Android Studio's
  bundled JBR is detected automatically.

## Build

Double-click (or run) **`build.bat`**. Output: `build\chess.apk` — zipaligned
and debug-signed, ready to install. The script:

1. compiles `res/` with `aapt2 compile`
2. links `AndroidManifest.xml` into an APK (`aapt2 link`, minSdk 26, targetSdk 36)
3. compiles the Java sources against `android.jar` (`javac --release 8`)
4. converts them to DEX (`d8`)
5. injects `classes.dex` and the `assets/` sound files
6. zipaligns and signs with `apksigner` (auto-generated debug keystore)
7. verifies the signature and prints the APK badging

## Test

**`test.bat`** runs the engine test suite: perft counts against five known
positions (covering castling, en passant and promotions), checkmate and
stalemate detection, exact undo restoration, AI legality and finding a
mate-in-one.

## Windows version (NVGT)

`windows/` contains the same game rebuilt as an **NVGT** script for Windows,
in the style of Street Fire Arena — it speaks with NVGT's **own built-in TTS
voice** (`use_sr = false`), plays the Blind Command logo at startup and asks
the same question with desktop gestures:

- **blind** → press **Enter twice** (a single Enter repeats the question)
- **sighted** → press the **Up arrow three times**

| Action             | Key                                        |
|--------------------|--------------------------------------------|
| Move cursor        | arrow keys                                 |
| Select / move      | Enter                                      |
| New game           | N (then 1 easy, 2 normal, 3 hard, 4 two players) |
| Undo               | U                                          |
| Quit               | Escape twice                               |
| Promotion          | 1 queen, 2 rook, 3 bishop, 4 knight        |

Blind mode announces every square the cursor touches ("e2, white pawn,
2 moves", "legal move to e4"); sighted mode prints the board to the console
after every change. Statuses (moves, check, mate) are spoken in both modes.

Build with **`windows/build_windows.bat`**: it compiles `chess.nvgt` and
`chess_selftest.nvgt` with `nvgt.exe`, unpacks the products and runs the
engine self-test (same perft/mate checks as the Java version). Output:
`windows/chess.exe`.

## Install

With a device connected (USB debugging enabled), run **`install.bat`**, or
copy `build\chess.apk` to the device and open it.

## Controls

| Action        | How                                     |
|---------------|-----------------------------------------|
| Move          | tap your piece, then tap a marked square |
| Promotion     | a dialog appears when a pawn reaches the last rank |
| New game      | "New game" button — choose opponent and difficulty |
| Undo          | "Undo" button (takes back the computer's reply too) |
| Flip board    | "Flip" button                           |

The computer plays black; in two-player mode the board can be flipped between
moves.
