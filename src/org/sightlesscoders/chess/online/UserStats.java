package org.sightlesscoders.chess.online;

import org.json.JSONException;
import org.json.JSONObject;

public class UserStats {
    public final String userId;
    public final String email;
    public final String displayName;
    public final int wins;
    public final int losses;
    public final int draws;
    public final int totalGames;
    public final long createdAt;
    public final long updatedAt;

    public UserStats(String userId, String email, String displayName,
                     int wins, int losses, int draws, long createdAt, long updatedAt) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.totalGames = wins + losses + draws;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserStats fromJson(String userId, JSONObject json) throws JSONException {
        return new UserStats(
                userId,
                json.optString("email", ""),
                json.optString("displayName", "Player"),
                json.optInt("wins", 0),
                json.optInt("losses", 0),
                json.optInt("draws", 0),
                json.optLong("createdAt", System.currentTimeMillis()),
                json.optLong("updatedAt", System.currentTimeMillis())
        );
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("displayName", displayName);
            json.put("wins", wins);
            json.put("losses", losses);
            json.put("draws", draws);
            json.put("createdAt", createdAt);
            json.put("updatedAt", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    public UserStats withWin() {
        return new UserStats(userId, email, displayName, wins + 1, losses, draws, createdAt, System.currentTimeMillis());
    }

    public UserStats withLoss() {
        return new UserStats(userId, email, displayName, wins, losses + 1, draws, createdAt, System.currentTimeMillis());
    }

    public UserStats withDraw() {
        return new UserStats(userId, email, displayName, wins, losses, draws + 1, createdAt, System.currentTimeMillis());
    }

    public UserStats withDisplayName(String newName) {
        return new UserStats(userId, email, newName, wins, losses, draws, createdAt, System.currentTimeMillis());
    }

    public double getWinRate() {
        if (totalGames == 0) return 0;
        return (double) wins / totalGames * 100;
    }

    @Override
    public String toString() {
        return String.format("%s: %dW-%dL-%dD (%.1f%%)", displayName, wins, losses, draws, getWinRate());
    }
}