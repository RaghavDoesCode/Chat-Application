package com.WeConnect.services;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.WeConnect.util.FileEncoder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/**
 * AuthService — handles everything related to user identity:
 *   - Registration (Firebase Auth + writing /users/{uid})
 *   - Login (resolving UID, loading profile data, setting online)
 *   - Logout (writing lastSeen, setting offline, clearing session)
 *   - Profile picture upload (Base64 data URI into /users/{uid}/profileImage)
 *   - Online/offline status writes
 *
 * Session state is stored as public static fields so all other services
 * and controllers can read the current user's UID, name, etc. without
 * passing it around in every call.
 *
 * DAA note (viva):
 *   HashMap used for building the user record on registration — O(1) average
 *   insert, same as the fan-out writes in ChatService.
 */
public class AuthService {

    // ── Session state — set on login, cleared on logout ──────────────────────
    public static String currentUserUID;
    public static String currentUserEmail;
    public static String currentUserName;
    public static String currentUserProfileBase64; // Base64 data URI or null

    private AuthService() {}

    // ─────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────

    /**
     * Creates a new Firebase Auth user and writes their profile to /users/{uid}.
     * Returns the new UID on success.
     */
    public static CompletableFuture<String> registerUser(String name, String email, String password) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord.CreateRequest req = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setDisplayName(name);
            UserRecord rec = auth.createUser(req);
            String uid = rec.getUid();

            Map<String, Object> user = new HashMap<>();
            user.put("uid",          uid);
            user.put("name",         name);
            user.put("email",        email);
            user.put("status",       "offline");
            user.put("profileImage", "default");
            user.put("lastSeen",     System.currentTimeMillis());

            FirebaseInitializer.getDatabase()
                .child("users").child(uid).setValueAsync(user);
            f.complete(uid);
        } catch (Exception e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    // ─────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────

    /**
     * Resolves a Firebase Auth user by email, populates session fields,
     * loads their saved profile picture, and marks them online.
     *
     * Note: Firebase Admin SDK login is email-lookup only (no password check
     * server-side in Admin SDK — password auth belongs in Firebase Client SDK).
     */
    public static CompletableFuture<String> loginUser(String email, String password) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            UserRecord rec = auth.getUserByEmail(email);
            currentUserUID   = rec.getUid();
            currentUserEmail = email;
            currentUserName  = rec.getDisplayName();
            setUserOnlineStatus(currentUserUID, "online");

            // Load saved profile picture from DB
            FirebaseInitializer.getDatabase()
                .child("users").child(currentUserUID).child("profileImage")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot s) {
                        String val = (String) s.getValue();
                        currentUserProfileBase64 =
                            (val != null && !val.equals("default")) ? val : null;
                        f.complete(currentUserUID);
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {
                        f.complete(currentUserUID); // proceed even if pic fails
                    }
                });

        } catch (Exception e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    // ─────────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────────

    /**
     * Writes lastSeen timestamp, sets status to offline, clears all session state.
     */
    public static void logout() {
        if (currentUserUID != null) {
            FirebaseInitializer.getDatabase()
                .child("users").child(currentUserUID)
                .child("lastSeen").setValueAsync(System.currentTimeMillis());
            setUserOnlineStatus(currentUserUID, "offline");
        }
        currentUserUID           = null;
        currentUserEmail         = null;
        currentUserName          = null;
        currentUserProfileBase64 = null;
    }

    // ─────────────────────────────────────────────
    // PROFILE PICTURE
    // ─────────────────────────────────────────────

    /**
     * Reads the image file, encodes it as a Base64 data URI, and saves it
     * to /users/{uid}/profileImage. No Firebase Storage needed.
     *
     * Runs on a background thread — never blocks the JavaFX thread.
     */
    public static CompletableFuture<String> uploadProfilePicture(File imageFile) {
        CompletableFuture<String> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                String ext     = FileEncoder.getExtension(imageFile.getName());
                String mime    = ext.equals("png") ? "image/png" : "image/jpeg";
                String dataUri = FileEncoder.encode(imageFile, "image");

                FirebaseInitializer.getDatabase()
                    .child("users").child(currentUserUID)
                    .child("profileImage").setValueAsync(dataUri);
                currentUserProfileBase64 = dataUri;
                f.complete(dataUri);
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        }).start();
        return f;
    }

    // ─────────────────────────────────────────────
    // ONLINE STATUS
    // ─────────────────────────────────────────────

    /** Writes "online" or "offline" to /users/{uid}/status. */
    public static void setUserOnlineStatus(String uid, String status) {
        FirebaseInitializer.getDatabase()
            .child("users").child(uid).child("status").setValueAsync(status);
    }
}