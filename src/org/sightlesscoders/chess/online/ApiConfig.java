package org.sightlesscoders.chess.online;

/** Firebase configuration. Replace with your project values. */
public final class ApiConfig {
    // Get these from Firebase Console > Project Settings > General > Your apps > Web app
    public static final String FIREBASE_API_KEY = "YOUR_FIREBASE_API_KEY";
    public static final String FIREBASE_PROJECT_ID = "YOUR_PROJECT_ID";
    public static final String FIREBASE_DATABASE_URL = "https://" + FIREBASE_PROJECT_ID + "-default-rtdb.firebaseio.com";

    private ApiConfig() {}
}