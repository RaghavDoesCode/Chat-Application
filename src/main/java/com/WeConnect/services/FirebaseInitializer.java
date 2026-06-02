package com.WeConnect.services;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * FirebaseInitializer — responsible for one thing only:
 * connecting the app to Firebase on startup.
 *
 * Call {@link #initialize()} once from Main.java before the JavaFX stage launches.
 * All other services call {@link #getDatabase()} to get a root reference.
 *
 * DAA note (viva): Firebase Realtime Database stores data as a JSON tree.
 * Every database path (e.g. "messages/uid1/uid2") is a root-to-leaf traversal
 * of that tree — classic tree traversal, O(depth).
 */
public class FirebaseInitializer {

    private static FirebaseDatabase database;

    private FirebaseInitializer() {}

    /**
     * Initializes the Firebase connection using the service account key bundled
     * in resources. Safe to call multiple times — skips if already initialized.
     *
     * serviceAccountKey.json must be at:
     *   src/main/resources/com/WeConnect/serviceAccountKey.json
     */
    public static void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                java.io.InputStream sa =
                    FirebaseInitializer.class.getResourceAsStream(
                        "/com/WeConnect/serviceAccountKey.json");

                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(sa))
                    // Replace with your actual Realtime DB URL from the Firebase Console
                    .setDatabaseUrl("https://weconnect-45e0d-default-rtdb.firebaseio.com")
                    .build();

                FirebaseApp.initializeApp(options);
                database = FirebaseDatabase.getInstance();
                System.out.println("[Firebase] Initialized OK");
            }
        } catch (IOException e) {
            System.err.println("[Firebase] Init error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Returns the root DatabaseReference. All services use this as their starting point. */
    public static DatabaseReference getDatabase() {
        return database.getReference();
    }
}