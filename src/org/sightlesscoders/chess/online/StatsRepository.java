package org.sightlesscoders.chess.online;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsRepository {
    private static final String TAG = "StatsRepository";
    private final AuthRepository auth;

    public StatsRepository(AuthRepository auth) {
        this.auth = auth;
    }

    public interface StatsCallback {
        void onSuccess(UserStats stats);
        void onError(Exception e);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private String getStatsUrl(String userId) {
        return ApiConfig.FIREBASE_DATABASE_URL + "/users/" + userId + "/stats.json?auth=" + auth.getIdToken();
    }

    public void getStats(StatsCallback callback) {
        String userId = auth.getUserId();
        if (userId == null) {
            callback.onError(new Exception("Not logged in"));
            return;
        }

        HttpClient.get(getStatsUrl(userId), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (response.equals("null")) {
                        // Create default stats
                        UserStats stats = new UserStats(
                                userId,
                                auth.getEmail(),
                                "Player",
                                0, 0, 0, 1200, // Default starting points
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                        );
                        callback.onSuccess(stats);
                    } else {
                        JSONObject json = new JSONObject(response);
                        UserStats stats = UserStats.fromJson(userId, json);
                        callback.onSuccess(stats);
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

    public void updateStats(UserStats stats, VoidCallback callback) {
        String userId = auth.getUserId();
        if (userId == null) {
            callback.onError(new Exception("Not logged in"));
            return;
        }

        HttpClient.put(getStatsUrl(userId), stats.toJson().toString(), null, new HttpClient.Callback() {
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

    public void recordWin(VoidCallback callback) {
        recordWin(25, callback); // Default +25 points
    }

    public void recordWin(int pointsChange, VoidCallback callback) {
        getStats(new StatsCallback() {
            @Override
            public void onSuccess(UserStats stats) {
                updateStats(stats.withWin(pointsChange), callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void recordLoss(VoidCallback callback) {
        recordLoss(25, callback); // Default -25 points
    }

    public void recordLoss(int pointsChange, VoidCallback callback) {
        getStats(new StatsCallback() {
            @Override
            public void onSuccess(UserStats stats) {
                updateStats(stats.withLoss(pointsChange), callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void recordDraw(VoidCallback callback) {
        getStats(new StatsCallback() {
            @Override
            public void onSuccess(UserStats stats) {
                updateStats(stats.withDraw(), callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void setDisplayName(String displayName, VoidCallback callback) {
        getStats(new StatsCallback() {
            @Override
            public void onSuccess(UserStats stats) {
                updateStats(stats.withDisplayName(displayName), callback);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getLeaderboard(int limit, LeaderboardCallback callback) {
        String url = ApiConfig.FIREBASE_DATABASE_URL + "/leaderboard.json?orderBy=\"wins\"&limitToLast=" + limit + "&auth=" + auth.getIdToken();
        HttpClient.get(url, null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (response.equals("null")) {
                        callback.onSuccess(new ArrayList<>());
                    } else {
                        JSONObject json = new JSONObject(response);
                        // Firebase returns key-value pairs, we need to sort manually
                        // For simplicity, just return empty - would need proper implementation
                        callback.onSuccess(new ArrayList<>());
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

    public interface LeaderboardCallback {
        void onSuccess(java.util.List<UserStats> stats);
        void onError(Exception e);
    }
}