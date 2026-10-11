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

The logo can be skipped at any time with **Tab twice**, **Space** or
**Enter**. After the question the **main menu** opens: play against the
computer, two players on one keyboard, host or join an **online game**,
command line mode, accounts, settings and help.

| Action             | Key                                        |
|--------------------|--------------------------------------------|
| Move cursor        | arrow keys (up/down/left/right)            |
| Diagonals          | Shift+Left/Right (up-left/up-right), Ctrl+Left/Right (down-left/down-right) |
| Select / move      | Enter                                      |
| New game           | N (then 1 easy, 2 normal, 3 hard, 4 two players) |
| Undo               | U                                          |
| Move list          | L                                          |
| Back to menu       | M                                          |
| Draw offer         | D (Shift+D rejects an offer)               |
| Chat (online)      | C                                          |
| Voice commands     | V (toggle, then speak squares and commands) |
| Resign             | R twice                                    |
| Check time         | T (Shift+T opponent's clock)               |
| Quit               | Escape twice                               |
| Promotion          | 1 queen, 2 rook, 3 bishop, 4 knight        |

Blind mode announces every square the cursor touches ("e2, white pawn,
2 moves", "legal move to e4"); sighted mode prints the board to the console
after every change. Every move is spoken as "White plays e4", "Black plays
e5", "White plays bishop c5", and every match announces itself when it
starts.

### Settings

The main menu's **Settings** item opens a dialog of checkboxes, sliders and
buttons built with **blindpro's `window_gui`** library (vendored in
`vendor/blindpro-window-gui`), which layers named controls over NVGT's audio
form. It holds:

- **Narrate the square under the cursor** — the blind-mode narration, on or
  off independently of the blind/sighted choice.
- **Voice command input** — the same switch as pressing **V** in a match.
- **Voice rate** (−10 to +10) and **Voice volume** (1–100%) — fed to NVGT's
  built-in voice. The volume slider is only applied once it is moved, because
  `tts.get_volume()` reports 0 on machines that use the OS default and would
  otherwise silence the game.

**OK** saves, **Cancel** or **Escape** leaves everything as it was.

### Voice commands

Press **V** in a match (or tick *Voice command input* in Settings) and the
game listens for NVGT's **voice_command** plugin (vendored in
`vendor/NVGT-Helpers`, its DLL ships in `windows/lib/` and is bundled into
`chess.zip`). Then play without touching the keyboard:

| Say            | Does                                        |
|----------------|---------------------------------------------|
| a square (e4, a8, h1 ...) | move the cursor there; with a piece picked up and the square legal, it plays the move |
| select         | pick up the piece under the cursor / play the move |
| undo, new, moves, menu, help | the same as U, N, L and the help text |
| resign         | resign                                      |
| draw, accept, reject | the same as D and Shift+D             |
| chat, time     | the same as C and T                         |
| queen, rook, bishop, knight | choose the promoting piece          |

So "e2", "e4" is a complete turn. If no speech recognizer is available the
game says so once and the keyboard keeps working exactly as before — nothing
in the game depends on the plugin being there.

### Online play, clocks and draw offers

One player **hosts** (port 47772) and the other **joins** with the host's
address; both must be signed in. Games carry a **time control** (bullet,
blitz, rapid or custom, with increment), the clocks sync over the network
and flag falls end the match. Either player can offer a **draw** (D),
**chat** (C) or **resign** (R twice). Wins, losses and draws update the
account (online wins +25 points, command line mode +50).

### Sounds

The Windows version ships with its own set of chess-like sound effects
(`windows/sounds/ui/`, synthesized — see `windows/_gensounds.py`):

| Sound      | When                                        |
|------------|---------------------------------------------|
| select     | picking a piece up                          |
| move       | a piece placed on the board                 |
| capture    | a capturing move (harder two-part clack)    |
| castle     | castling (two quick clacks)                 |
| promote    | promotion (rising chime)                    |
| check      | a check is given (alert tone)               |
| start      | a new match begins                          |
| win / lose / draw | the result of the match              |
| flag       | a clock runs out of time                    |
| click / error | menu navigation / invalid action         |

Build with **`windows/build_windows.bat`**: it compiles `chess.nvgt` and
`chess_selftest.nvgt` with `nvgt.exe`, unpacks the products and runs the
engine self-test (same perft/mate checks as the Java version, now registered
through the vendored **nv-test** framework, which prints one line per test,
writes `chess_selftest.log` and returns the failure count as the exit code).
Output: `windows/chess.exe`.

Building needs three libraries vendored in `vendor/` (included as plain
source, no submodules) and `voice_command.dll` available to the compiler —
copy `vendor/NVGT-Helpers/plugins/voice_command.dll` into `C:\nvgt\lib\` once
if NVGT complains that it failed to load the plugin.

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

## License

Do whatever you want with it — see [LICENSE](LICENSE). In short: host it,
break it, fix it, sell it, give it away, make something else out of it; no
permission needed and no credit required.

**Third-party code keeps its own license.** The three libraries in `vendor/`
— blindpro's `window_gui`, `NVGT-Helpers` (voice commands) and `nv-test` —
are all distributed under **GPLv3**, and that does not change here. Because
the Windows build compiles them into `chess.exe`, distributing that binary
carries the usual GPLv3 obligations (notably, source must be available under
the same terms). NVGT itself and its plugins are separate again; check their
own terms before shipping.
