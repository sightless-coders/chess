package org.sightlesscoders.chess.online;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private static final String TAG = "AuthRepository";
    private static final String PREFS_NAME = "chess_auth";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final Context context;
    private final SharedPreferences prefs;

    private String idToken;
    private String refreshToken;
    private String userId;
    private String email;
    private long expiresAt;

    public AuthRepository(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadTokens();
    }

    private void loadTokens() {
        idToken = prefs.getString(KEY_ID_TOKEN, null);
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null);
        userId = prefs.getString(KEY_USER_ID, null);
        email = prefs.getString(KEY_EMAIL, null);
        expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
    }

    private void saveTokens() {
        prefs.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply();
    }

    public void clearTokens() {
        idToken = null;
        refreshToken = null;
        userId = null;
        email = null;
        expiresAt = 0;
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return idToken != null && userId != null && System.currentTimeMillis() < expiresAt;
    }

    public String getIdToken() {
        if (isLoggedIn()) return idToken;
        if (refreshToken != null) {
            // Try to refresh
            return null; // Caller should call refreshToken()
        }
        return null;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public interface AuthCallback {
        void onSuccess(String idToken, String userId, String email);
        void onError(Exception e);
    }

    public void signUp(String email, String password, AuthCallback callback) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + ApiConfig.FIREBASE_API_KEY;
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("returnSecureToken", true);
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.post(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    idToken = json.getString("idToken");
                    refreshToken = json.getString("refreshToken");
                    userId = json.getString("localId");
                    long expiresIn = json.getLong("expiresIn");
                    expiresAt = System.currentTimeMillis() + expiresIn * 1000;
                    AuthRepository.this.email = email;
                    saveTokens();
                    callback.onSuccess(idToken, userId, email);
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

    public void signIn(String email, String password, AuthCallback callback) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + ApiConfig.FIREBASE_API_KEY;
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("returnSecureToken", true);
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.post(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    idToken = json.getString("idToken");
                    refreshToken = json.getString("refreshToken");
                    userId = json.getString("localId");
                    long expiresIn = json.getLong("expiresIn");
                    expiresAt = System.currentTimeMillis() + expiresIn * 1000;
                    AuthRepository.this.email = json.getString("email");
                    saveTokens();
                    callback.onSuccess(idToken, userId, AuthRepository.this.email);
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

    public void signInAnonymously(AuthCallback callback) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + ApiConfig.FIREBASE_API_KEY;
        JSONObject body = new JSONObject();
        try {
            body.put("returnSecureToken", true);
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.post(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    idToken = json.getString("idToken");
                    refreshToken = json.getString("refreshToken");
                    userId = json.getString("localId");
                    long expiresIn = json.getLong("expiresIn");
                    expiresAt = System.currentTimeMillis() + expiresIn * 1000;
                    email = "anonymous_" + userId.substring(0, 8) + "@chess.local";
                    saveTokens();
                    callback.onSuccess(idToken, userId, email);
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

    public void refreshToken(AuthCallback callback) {
        if (refreshToken == null) {
            callback.onError(new Exception("No refresh token"));
            return;
        }

        String url = "https://securetoken.googleapis.com/v1/token?key=" + ApiConfig.FIREBASE_API_KEY;
        JSONObject body = new JSONObject();
        try {
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", refreshToken);
        } catch (JSONException e) {
            callback.onError(e);
            return;
        }

        HttpClient.post(url, body.toString(), null, new HttpClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    idToken = json.getString("id_token");
                    refreshToken = json.getString("refresh_token");
                    userId = json.getString("user_id");
                    long expiresIn = json.getLong("expires_in");
                    expiresAt = System.currentTimeMillis() + expiresIn * 1000;
                    saveTokens();
                    callback.onSuccess(idToken, userId, email);
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

    public void signOut() {
        clearTokens();
    }
}