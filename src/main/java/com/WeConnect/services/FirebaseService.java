package com.WeConnect.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * FirebaseService — singleton that wraps all Firebase operations.
 *
 * DAA Concepts used here:
 *  - HashMap for O(1) key-value storage (message body, user data)
 *  - Tree traversal via Firebase path addressing (JSON tree)
 *  - Fan-out write (space-time tradeoff): dual-write for O(1) reads
 */
public class FirebaseService {

    private static FirebaseService instance;
    private static FirebaseDatabase database;
    private static FirebaseAuth auth;

    // Currently logged-in user
    public static String currentUserUID;
    public static String currentUserEmail;
    public static String currentUserName;

    private FirebaseService() {}

    public static FirebaseService getInstance() {
        if (instance == null) {
            instance = new FirebaseService();
        }
        return instance;
    }

    /**
     * Initialize Firebase with your service account credentials.
     * Replace the path with your actual google-services JSON / service account file.
     */
    public static void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                java.io.InputStream serviceAccount =
                    FirebaseService.class.getResourceAsStream("/com/WeConnect/serviceAccountKey.json");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://weconnect-45e0d-default-rtdb.firebaseio.com")
                    .build();

                FirebaseApp.initializeApp(options);
                database = FirebaseDatabase.getInstance();
                auth = FirebaseAuth.getInstance();
                System.out.println("Firebase initialized successfully.");
            }
        } catch (IOException e) {
            System.err.println("Firebase init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static DatabaseReference getDatabase() {
        return database.getReference();
    }

    // ─────────────────────────────────────────────
    // AUTH
    // ─────────────────────────────────────────────

    /**
     * Register a new user.
     * Stores user profile in /users/{uid} as a HashMap (O(1) insert).
     */
    public CompletableFuture<String> registerUser(String name, String email, String password) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(name);

            UserRecord userRecord = auth.createUser(request);
            String uid = userRecord.getUid();

            // Store user profile in DB — HashMap for O(1) lookup later
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("name", name);
            userMap.put("email", email);
            userMap.put("uid", uid);
            userMap.put("status", "offline");
            userMap.put("profileImage", "default");

            database.getReference("users").child(uid)
                    .setValueAsync(userMap);

            future.complete(uid);
        } catch (Exception e) {
            System.err.println("Register error: " + e.getMessage());
            e.printStackTrace();
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Login — fetches user record by email, verifies, sets currentUser fields.
     */
    public CompletableFuture<String> loginUser(String email, String password) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            UserRecord userRecord = auth.getUserByEmail(email);
            // Note: Firebase Admin SDK doesn't do password verification directly.
            // In production use Firebase REST API for client-side auth.
            // For demo: we trust the email lookup as "login".
            currentUserUID = userRecord.getUid();
            currentUserEmail = email;
            currentUserName = userRecord.getDisplayName();
            setUserOnlineStatus(currentUserUID, "online");
            future.complete(currentUserUID);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public void logout() {
        if (currentUserUID != null) {
            setUserOnlineStatus(currentUserUID, "offline");
        }
        currentUserUID = null;
        currentUserEmail = null;
        currentUserName = null;
    }

    // ─────────────────────────────────────────────
    // MESSAGES
    // ─────────────────────────────────────────────

    /**
     * Send a message — core DAA concept: FAN-OUT WRITE (Space-Time Tradeoff)
     *
     * Instead of one write + a join-query at read time (slow),
     * we write to BOTH sender and receiver paths simultaneously.
     * Doubles storage (space cost) but makes reads O(1) (time gain).
     *
     * Path structure (Tree):
     *   messages/{senderUID}/{receiverUID}/{messageID}  ← sender's copy
     *   messages/{receiverUID}/{senderUID}/{messageID}  ← receiver's copy
     */
    public void sendMessage(String receiverUID, String messageText) {
        DatabaseReference msgRef = database.getReference("messages")
                .child(currentUserUID)
                .child(receiverUID)
                .push(); // generates unique chronological key (greedy timestamp)

        String messageId = msgRef.getKey();
        long timestamp = System.currentTimeMillis();

        // HashMap — O(1) insert for each field
        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("message", messageText);
        messageBody.put("type", "text");
        messageBody.put("from", currentUserUID);
        messageBody.put("to", receiverUID);
        messageBody.put("seen", false);
        messageBody.put("time", timestamp);

        // Fan-out: write to BOTH paths atomically
        Map<String, Object> fanOutMap = new HashMap<>();
        fanOutMap.put("messages/" + currentUserUID + "/" + receiverUID + "/" + messageId, messageBody);
        fanOutMap.put("messages/" + receiverUID + "/" + currentUserUID + "/" + messageId, messageBody);

        database.getReference().updateChildrenAsync(fanOutMap);
    }

    /**
     * Listen for messages between current user and a friend.
     * Firebase ChildEventListener fires whenever a new child is added —
     * this is the "real-time" part. No polling.
     */
    public void listenForMessages(String friendUID, MessageListener listener) {
        database.getReference("messages")
                .child(currentUserUID)
                .child(friendUID)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        String text = (String) snapshot.child("message").getValue();
                        String from = (String) snapshot.child("from").getValue();
                        long time = snapshot.child("time").getValue(Long.class);
                        listener.onNewMessage(from, text, time);
                    }
                    @Override public void onChildChanged(DataSnapshot s, String p) {}
                    @Override public void onChildRemoved(DataSnapshot s) {}
                    @Override public void onChildMoved(DataSnapshot s, String p) {}
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    // ─────────────────────────────────────────────
    // FRIEND REQUESTS  (Directed Graph model)
    //
    // DAA Concept: Graph — users are nodes, requests are directed edges.
    // Accepting a request makes the edge bidirectional (friendship).
    //
    // Path structure:
    //   friend_requests/{receiverUID}/{senderUID} = "sent"
    //   friend_requests/{senderUID}/{receiverUID} = "received"
    // ─────────────────────────────────────────────

    public void sendFriendRequest(String receiverUID) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("friend_requests/" + receiverUID + "/" + currentUserUID, "sent");
        requestMap.put("friend_requests/" + currentUserUID + "/" + receiverUID, "received");
        database.getReference().updateChildrenAsync(requestMap);
    }

    public void acceptFriendRequest(String senderUID) {
        // Remove request edges, create friendship edges
        Map<String, Object> acceptMap = new HashMap<>();
        acceptMap.put("friend_requests/" + currentUserUID + "/" + senderUID, null); // delete
        acceptMap.put("friend_requests/" + senderUID + "/" + currentUserUID, null); // delete
        acceptMap.put("friends/" + currentUserUID + "/" + senderUID, "friend");
        acceptMap.put("friends/" + senderUID + "/" + currentUserUID, "friend");
        database.getReference().updateChildrenAsync(acceptMap);
    }

    public void declineFriendRequest(String senderUID) {
        database.getReference("friend_requests").child(currentUserUID).child(senderUID).removeValueAsync();
        database.getReference("friend_requests").child(senderUID).child(currentUserUID).removeValueAsync();
    }

    // ─────────────────────────────────────────────
    // USERS
    // ─────────────────────────────────────────────

    /**
     * Search users by name — Linear Search O(n).
     * DAA talking point: scales poorly; Trie or inverted index would be O(k).
     */
    public void searchUsers(String query, UserSearchListener listener) {
        database.getReference("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String name = (String) userSnap.child("name").getValue();
                    String uid = (String) userSnap.child("uid").getValue();
                    String email = (String) userSnap.child("email").getValue();
                    // Linear search — O(n) scan
                    if (name != null && name.toLowerCase().contains(query.toLowerCase())
                            && !uid.equals(currentUserUID)) {
                        listener.onUserFound(uid, name, email);
                    }
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    public void getAllFriends(UserSearchListener listener) {
        database.getReference("friends").child(currentUserUID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot friendSnap : snapshot.getChildren()) {
                            String friendUID = friendSnap.getKey();
                            // Fetch friend details
                            database.getReference("users").child(friendUID)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot userSnap) {
                                            String name = (String) userSnap.child("name").getValue();
                                            String email = (String) userSnap.child("email").getValue();
                                            listener.onUserFound(friendUID, name, email);
                                        }
                                        @Override public void onCancelled(DatabaseError e) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
    }

    public void getIncomingRequests(UserSearchListener listener) {
        database.getReference("friend_requests").child(currentUserUID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot reqSnap : snapshot.getChildren()) {
                            String status = (String) reqSnap.getValue();
                            if ("sent".equals(status)) { // "sent" means they sent to us
                                String senderUID = reqSnap.getKey();
                                database.getReference("users").child(senderUID)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(DataSnapshot userSnap) {
                                                String name = (String) userSnap.child("name").getValue();
                                                String email = (String) userSnap.child("email").getValue();
                                                listener.onUserFound(senderUID, name, email);
                                            }
                                            @Override public void onCancelled(DatabaseError e) {}
                                        });
                            }
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
    }

    private void setUserOnlineStatus(String uid, String status) {
        database.getReference("users").child(uid).child("status").setValueAsync(status);
    }

    // ─────────────────────────────────────────────
    // LISTENER INTERFACES
    // ─────────────────────────────────────────────

    public interface MessageListener {
        void onNewMessage(String fromUID, String message, long timestamp);
    }

    public interface UserSearchListener {
        void onUserFound(String uid, String name, String email);
    }
}
