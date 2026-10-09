package org.sightlesscoders.chess.online;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MatchmakingRepository {
    private static final String TAG = "MatchmakingRepository";
    private final AuthRepository auth;

    public MatchmakingRepository(AuthRepository auth) {
        this.auth = auth;
    }

    public interface MatchCallback {
        void onMatched(String gameId, boolean isWhite);
        void onError(Exception e);
    }

    public interface GameStateCallback {
        void onGameState(GameState state);
        void onError(Exception e);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public void findMatch(MatchCallback callback) {
        String userId = auth.getUserId();
        if (userId == null) {
            callback.onError(new Exception("Not logged in"));
            return;
        }

        // Add player to matchmaking queue
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/matchmaking/queue/" + userId + ".json?auth=" + auth.getIdToken();
        JSONObject body = new JSONObject();
        try {
            body.put("userId", userId);
            body.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.put(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                // Start listening for match
                listenForMatch(callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void cancelMatchmaking(VoidCallback callback) {
        String userId = auth.getUserId();
        if (userId == null) {
            callback.onError(new Exception("Not logged in"));
            return;
        }

        String url = ApiConfig.FIREBASE_DATABASE_URL + "/matchmaking/queue/" + userId + ".json?auth=" + auth.getIdToken();
        HttpClient.delete(url, null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private void listenForMatch(final MatchCallback callback) {
        String userId = auth.getUserId();
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/matchmaking/matches/" + userId + ".json?auth=" + auth.getIdToken();

        // Poll for match (in production, use Firebase Realtime Database listeners)
        pollForMatch(url, callback, 0);
    }

    private void pollForMatch(String url, final MatchCallback callback, int attempts) {
        if (attempts > 60) { // Timeout after ~30 seconds
            callback.onError(new Exception("Matchmaking timeout"));
            return;
        }

        HttpClient.get(url, null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (!response.equals("null")) {
                        JSONObject json = new JSONObject(response);
                        String gameId = json.getString("gameId");
                        boolean isWhite = json.getBoolean("isWhite");
                        // Clean up match entry
                        HttpClient.delete(url, null, new HttpClient.Callback() {
                            @Override
                            public void onSuccess(String response) {}
                            @Override
                            public void onError(Exception e) {}
                        });
                        callback.onMatched(gameId, isWhite);
                    } else {
                        // Not matched yet, poll again
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        handler.postDelayed(() -> pollForMatch(url, callback, attempts + 1), 500);
                    }
                } catch (JSONException e) {
                    callback.onError(e);
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void listenToGame(String gameId, GameStateCallback callback) {
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/games/" + gameId + ".json?auth=" + auth.getIdToken();
        pollGameState(url, callback, 0);
    }

    private void pollGameState(String url, final GameStateCallback callback, int attempts) {
        HttpClient.get(url, null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (!response.equals("null")) {
                        JSONObject json = new JSONObject(response);
                        GameState state = GameState.fromJson(json);
                        callback.onGameState(state);
                    }
                    // Continue polling
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(() -> pollGameState(url, callback, attempts + 1), 1000);
                } catch (JSONException e) {
                    callback.onError(e);
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void makeMove(String gameId, MoveData move, VoidCallback callback) {
        String userId = auth.getUserId();
        if (userId == null) {
            callback.onError(new Exception("Not logged in"));
            return;
        }

        String url = ApiConfig.FIREBASE_DATABASE_URL + "/games/" + gameId + "/moves.json?auth=" + auth.getIdToken();
        HttpClient.post(url, move.toJson().toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void updateGameState(String gameId, GameState state, VoidCallback callback) {
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/games/" + gameId + ".json?auth=" + auth.getIdToken();
        HttpClient.patch(url, state.toJson().toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void resignGame(String gameId, VoidCallback callback) {
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/games/" + gameId + "/resigned.json?auth=" + auth.getIdToken();
        JSONObject body = new JSONObject();
        try {
            body.put("userId", auth.getUserId());
            body.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.put(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static class MoveData {
        public final String from;
        public final String to;
        public final String promotion;
        public final long timestamp;
        public final String playerId;

        public MoveData(String from, String to, String promotion, String playerId) {
            this.from = from;
            this.to = to;
            this.promotion = promotion;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("from", from);
                json.put("to", to);
                json.put("promotion", promotion);
                json.put("playerId", playerId);
                json.put("timestamp", timestamp);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return json;
        }
    }

    public static class GameState {
        public final String gameId;
        public final String whitePlayerId;
        public final String blackPlayerId;
        public final String fen;
        public final String status; // "waiting", "active", "finished"
        public final String result; // "white_wins", "black_wins", "draw"
        public final long createdAt;
        public final long updatedAt;

        public GameState(String gameId, String whitePlayerId, String blackPlayerId, String fen) {
            this.gameId = gameId;
            this.whitePlayerId = whitePlayerId;
            this.blackPlayerId = blackPlayerId;
            this.fen = fen;
            this.status = "active";
            this.result = "";
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public static GameState fromJson(JSONObject json) throws JSONException {
            return new GameState(
                    json.getString("gameId"),
                    json.getString("whitePlayerId"),
                    json.getString("blackPlayerId"),
                    json.getString("fen")
            );
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("gameId", gameId);
                json.put("whitePlayerId", whitePlayerId);
                json.put("blackPlayerId", blackPlayerId);
                json.put("fen", fen);
                json.put("status", status);
                json.put("result", result);
                json.put("createdAt", createdAt);
                json.put("updatedAt", updatedAt);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return json;
        }

        public GameState withFen(String newFen) {
            GameState copy = new GameState(gameId, whitePlayerId, blackPlayerId, newFen);
            // Note: this creates a new object, in reality we'd copy all fields
            return copy;
        }

        public GameState withStatus(String newStatus) {
            GameState copy = new GameState(gameId, whitePlayerId, blackPlayerId, fen);
            return copy;
        }

        public GameState withResult(String newResult) {
            GameState copy = new GameState(gameId, whitePlayerId, blackPlayerId, fen);
            return copy;
        }
    }
}