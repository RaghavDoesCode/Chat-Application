package com.WeConnect.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
 * FirebaseService v2
 * - NO Firebase Storage needed (works on free Spark plan)
 * - Files/images/audio stored as Base64 data URIs in Realtime DB
 * - 500 KB per file limit enforced before encoding
 *
 * DAA Concepts used (mention in viva):
 *   HashMap        -> O(1) average insert/lookup for message bodies
 *   Fan-out write  -> Space-Time Tradeoff: dual-write for O(1) reads per user
 *   Directed Graph -> friend_requests are directed edges; friends = undirected
 *   Linear Search  -> searchUsers is O(n); Trie would give O(k)
 *   Tree traversal -> Firebase JSON is a tree; paths are root-to-leaf traversals
 */
public class FirebaseService {

    private static FirebaseService instance;
    private static FirebaseDatabase database;
    private static FirebaseAuth     auth;

    // 500 KB max per file (Base64 inflates ~33%, so raw file must be under this)
    private static final long MAX_FILE_BYTES = 500 * 1024L;

    // ── Session state (set on login) ──────────────
    public static String currentUserUID;
    public static String currentUserEmail;
    public static String currentUserName;
    public static String currentUserProfileBase64; // data URI or null

    private FirebaseService() {}

    public static FirebaseService getInstance() {
        if (instance == null) instance = new FirebaseService();
        return instance;
    }

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────

    /**
     * Call once from Main.java before launching the JavaFX stage.
     * Make sure serviceAccountKey.json is inside:
     *   src/main/resources/com/WeConnect/serviceAccountKey.json
     *
     * Also replace the databaseUrl below with YOUR project's URL.
     * Find it: Firebase Console -> Realtime Database -> Data tab (top of page).
     */
    public static void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                java.io.InputStream sa =
                    FirebaseService.class.getResourceAsStream(
                        "/com/WeConnect/serviceAccountKey.json");

                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(sa))
                    // ↓ REPLACE with your actual Realtime DB URL
                    .setDatabaseUrl("https://weconnect-45e0d-default-rtdb.firebaseio.com")
                    .build();

                FirebaseApp.initializeApp(options);
                database = FirebaseDatabase.getInstance();
                auth     = FirebaseAuth.getInstance();
                System.out.println("[Firebase] Initialized OK");
            }
        } catch (IOException e) {
            System.err.println("[Firebase] Init error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static DatabaseReference getDatabase() { return database.getReference(); }

    // ─────────────────────────────────────────────
    // AUTH
    // ─────────────────────────────────────────────

    public CompletableFuture<String> registerUser(String name, String email, String password) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            UserRecord.CreateRequest req = new UserRecord.CreateRequest()
                .setEmail(email).setPassword(password).setDisplayName(name);
            UserRecord rec = auth.createUser(req);
            String uid = rec.getUid();

            Map<String, Object> user = new HashMap<>();
            user.put("uid",          uid);
            user.put("name",         name);
            user.put("email",        email);
            user.put("status",       "offline");
            user.put("profileImage", "default");
            user.put("lastSeen",     System.currentTimeMillis());

            database.getReference("users").child(uid).setValueAsync(user);
            f.complete(uid);
        } catch (Exception e) { f.completeExceptionally(e); }
        return f;
    }

    public CompletableFuture<String> loginUser(String email, String password) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            UserRecord rec = auth.getUserByEmail(email);
            currentUserUID   = rec.getUid();
            currentUserEmail = email;
            currentUserName  = rec.getDisplayName();
            setUserOnlineStatus(currentUserUID, "online");

            // Load saved profile pic
            database.getReference("users").child(currentUserUID).child("profileImage")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot s) {
                        String val = (String) s.getValue();
                        currentUserProfileBase64 =
                            (val != null && !val.equals("default")) ? val : null;
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });

            f.complete(currentUserUID);
        } catch (Exception e) { f.completeExceptionally(e); }
        return f;
    }

    public void logout() {
        if (currentUserUID != null) {
            database.getReference("users").child(currentUserUID)
                .child("lastSeen").setValueAsync(System.currentTimeMillis());
            setUserOnlineStatus(currentUserUID, "offline");
        }
        currentUserUID           = null;
        currentUserEmail         = null;
        currentUserName          = null;
        currentUserProfileBase64 = null;
    }

    // ─────────────────────────────────────────────
    // PROFILE PICTURE  (Base64 data URI)
    // ─────────────────────────────────────────────

    /**
     * Reads an image file, encodes it as a Base64 data URI, and saves it
     * under /users/{uid}/profileImage in the Realtime DB.
     * No Firebase Storage needed.
     */
    public CompletableFuture<String> uploadProfilePicture(File imageFile) {
        CompletableFuture<String> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                if (imageFile.length() > MAX_FILE_BYTES) {
                    f.completeExceptionally(
                        new Exception("Image too large. Please use an image under 500 KB."));
                    return;
                }
                byte[] bytes  = Files.readAllBytes(imageFile.toPath());
                String b64    = Base64.getEncoder().encodeToString(bytes);
                String ext    = getExtension(imageFile.getName());
                String mime   = ext.equals("png") ? "image/png" : "image/jpeg";
                String dataUri = "data:" + mime + ";base64," + b64;

                database.getReference("users").child(currentUserUID)
                    .child("profileImage").setValueAsync(dataUri);
                currentUserProfileBase64 = dataUri;
                f.complete(dataUri);
            } catch (Exception e) { f.completeExceptionally(e); }
        }).start();
        return f;
    }

    // ─────────────────────────────────────────────
    // MESSAGES — TEXT
    // ─────────────────────────────────────────────

    public void sendMessage(String receiverUID, String text) {
        sendInternal(receiverUID, text, "text", null);
    }

    private void sendInternal(String receiverUID, String content,
                               String type, String fileName) {
        DatabaseReference ref = database.getReference("messages")
            .child(currentUserUID).child(receiverUID).push();
        String msgId = ref.getKey();
        long   ts    = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("message",  content);
        body.put("type",     type);
        body.put("from",     currentUserUID);
        body.put("to",       receiverUID);
        body.put("seen",     false);
        body.put("time",     ts);
        if (fileName != null) body.put("fileName", fileName);

        // Fan-out write (Space-Time Tradeoff)
        Map<String, Object> fanOut = new HashMap<>();
        fanOut.put("messages/" + currentUserUID + "/" + receiverUID + "/" + msgId, body);
        fanOut.put("messages/" + receiverUID + "/" + currentUserUID + "/" + msgId, body);

        // Last-message preview
        Map<String, Object> last = new HashMap<>();
        last.put("text", type.equals("text") ? content : "[" + type + "]");
        last.put("time", ts);
        fanOut.put("last_messages/" + currentUserUID + "/" + receiverUID, last);
        fanOut.put("last_messages/" + receiverUID + "/" + currentUserUID, last);

        database.getReference().updateChildrenAsync(fanOut);
    }

    // ─────────────────────────────────────────────
    // MESSAGES — FILES / IMAGES / AUDIO  (Base64)
    // ─────────────────────────────────────────────

    /**
     * Encodes a file as a Base64 data URI and sends it as a chat message.
     * messageType: "image" | "file" | "audio"
     */
    public CompletableFuture<Void> sendFileMessage(String receiverUID,
                                                    File file,
                                                    String messageType) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                if (file.length() > MAX_FILE_BYTES) {
                    f.completeExceptionally(
                        new Exception("File too large. Max is 500 KB."));
                    return;
                }
                String dataUri = encodeFile(file, messageType);
                sendInternal(receiverUID, dataUri, messageType, file.getName());
                f.complete(null);
            } catch (Exception e) { f.completeExceptionally(e); }
        }).start();
        return f;
    }

    // ─────────────────────────────────────────────
    // MESSAGES — LISTEN
    // ─────────────────────────────────────────────

    public void listenForMessages(String friendUID, MessageListener listener) {
        database.getReference("messages")
            .child(currentUserUID).child(friendUID)
            .addChildEventListener(new ChildEventListener() {
                @Override public void onChildAdded(DataSnapshot s, String prev) {
                    String text = (String) s.child("message").getValue();
                    String from = (String) s.child("from").getValue();
                    String type = (String) s.child("type").getValue();
                    String file = (String) s.child("fileName").getValue();
                    long   time = s.child("time").getValue(Long.class);
                    listener.onNewMessage(from, text, time,
                        type != null ? type : "text", file);
                }
                @Override public void onChildChanged(DataSnapshot s, String p) {}
                @Override public void onChildRemoved(DataSnapshot s) {}
                @Override public void onChildMoved(DataSnapshot s, String p) {}
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    public void markMessagesAsSeen(String friendUID) {
        database.getReference("messages").child(currentUserUID).child(friendUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot msg : snapshot.getChildren()) {
                        String from = (String) msg.child("from").getValue();
                        if (friendUID.equals(from)) {
                            msg.getRef().child("seen").setValueAsync(true);
                            database.getReference("messages")
                                .child(friendUID).child(currentUserUID)
                                .child(msg.getKey()).child("seen").setValueAsync(true);
                        }
                    }
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    // ─────────────────────────────────────────────
    // GROUP CHATS
    // ─────────────────────────────────────────────

    public CompletableFuture<String> createGroup(String groupName, List<String> memberUIDs) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            DatabaseReference gRef = database.getReference("groups").push();
            String groupId = gRef.getKey();

            Map<String, Object> members = new HashMap<>();
            members.put(currentUserUID, true);
            for (String uid : memberUIDs) members.put(uid, true);

            Map<String, Object> data = new HashMap<>();
            data.put("name",      groupName);
            data.put("createdBy", currentUserUID);
            data.put("createdAt", System.currentTimeMillis());
            data.put("members",   members);
            gRef.setValueAsync(data);

            Map<String, Object> memberUpdates = new HashMap<>();
            memberUpdates.put("user_groups/" + currentUserUID + "/" + groupId, groupName);
            for (String uid : memberUIDs)
                memberUpdates.put("user_groups/" + uid + "/" + groupId, groupName);
            database.getReference().updateChildrenAsync(memberUpdates);

            f.complete(groupId);
        } catch (Exception e) { f.completeExceptionally(e); }
        return f;
    }

    public void sendGroupMessage(String groupId, String content,
                                  String type, String fileName) {
        DatabaseReference ref = database.getReference("group_messages").child(groupId).push();
        long ts = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("message",    content);
        body.put("type",       type != null ? type : "text");
        body.put("from",       currentUserUID);
        body.put("senderName", currentUserName);
        body.put("time",       ts);
        body.put("seen",       false);
        if (fileName != null) body.put("fileName", fileName);
        ref.setValueAsync(body);

        Map<String, Object> last = new HashMap<>();
        last.put("text", "text".equals(type) ? content : "[" + type + "]");
        last.put("time", ts);
        database.getReference("group_last_messages").child(groupId).setValueAsync(last);
    }

    public void listenForGroupMessages(String groupId, GroupMessageListener listener) {
        database.getReference("group_messages").child(groupId)
            .addChildEventListener(new ChildEventListener() {
                @Override public void onChildAdded(DataSnapshot s, String prev) {
                    String text   = (String) s.child("message").getValue();
                    String from   = (String) s.child("from").getValue();
                    String sender = (String) s.child("senderName").getValue();
                    String type   = (String) s.child("type").getValue();
                    String file   = (String) s.child("fileName").getValue();
                    long   time   = s.child("time").getValue(Long.class);
                    listener.onNewGroupMessage(from, sender, text, time,
                        type != null ? type : "text", file);
                }
                @Override public void onChildChanged(DataSnapshot s, String p) {}
                @Override public void onChildRemoved(DataSnapshot s) {}
                @Override public void onChildMoved(DataSnapshot s, String p) {}
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    public CompletableFuture<Void> sendGroupFileMessage(String groupId,
                                                         File file,
                                                         String messageType) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                if (file.length() > MAX_FILE_BYTES) {
                    f.completeExceptionally(new Exception("File too large. Max 500 KB."));
                    return;
                }
                String dataUri = encodeFile(file, messageType);
                sendGroupMessage(groupId, dataUri, messageType, file.getName());
                f.complete(null);
            } catch (Exception e) { f.completeExceptionally(e); }
        }).start();
        return f;
    }

    public void getUserGroups(GroupListener listener) {
        database.getReference("user_groups").child(currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot s) {
                    for (DataSnapshot g : s.getChildren())
                        listener.onGroupFound(g.getKey(), (String) g.getValue());
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    // ─────────────────────────────────────────────
    // FRIEND REQUESTS  (Directed Graph edges)
    // ─────────────────────────────────────────────

    public void sendFriendRequest(String receiverUID) {
        Map<String, Object> m = new HashMap<>();
        m.put("friend_requests/" + receiverUID   + "/" + currentUserUID, "sent");
        m.put("friend_requests/" + currentUserUID + "/" + receiverUID,   "received");
        database.getReference().updateChildrenAsync(m);
    }

    public void acceptFriendRequest(String senderUID) {
        Map<String, Object> m = new HashMap<>();
        m.put("friend_requests/" + currentUserUID + "/" + senderUID,   null);
        m.put("friend_requests/" + senderUID   + "/" + currentUserUID, null);
        m.put("friends/" + currentUserUID + "/" + senderUID,   "friend");
        m.put("friends/" + senderUID   + "/" + currentUserUID, "friend");
        database.getReference().updateChildrenAsync(m);
    }

    public void declineFriendRequest(String senderUID) {
        database.getReference("friend_requests")
            .child(currentUserUID).child(senderUID).removeValueAsync();
        database.getReference("friend_requests")
            .child(senderUID).child(currentUserUID).removeValueAsync();
    }

    // ─────────────────────────────────────────────
    // USERS
    // ─────────────────────────────────────────────

    /** O(n) linear search — mention Trie O(k) in viva */
    public void searchUsers(String query, UserSearchListener listener) {
        database.getReference("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot u : snapshot.getChildren()) {
                    String name  = (String) u.child("name").getValue();
                    String uid   = (String) u.child("uid").getValue();
                    String email = (String) u.child("email").getValue();
                    String pic   = (String) u.child("profileImage").getValue();
                    if (name != null
                            && name.toLowerCase().contains(query.toLowerCase())
                            && !uid.equals(currentUserUID))
                        listener.onUserFound(uid, name, email, pic);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    public void getAllFriends(UserSearchListener listener) {
        database.getReference("friends").child(currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot fs : snapshot.getChildren()) {
                        String fid = fs.getKey();
                        database.getReference("users").child(fid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(DataSnapshot s) {
                                    listener.onUserFound(fid,
                                        (String) s.child("name").getValue(),
                                        (String) s.child("email").getValue(),
                                        (String) s.child("profileImage").getValue());
                                }
                                @Override public void onCancelled(DatabaseError e) {}
                            });
                    }
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    public void getIncomingRequests(UserSearchListener listener) {
        database.getReference("friend_requests").child(currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot r : snapshot.getChildren()) {
                        if ("sent".equals(r.getValue())) {
                            String sid = r.getKey();
                            database.getReference("users").child(sid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(DataSnapshot s) {
                                        listener.onUserFound(sid,
                                            (String) s.child("name").getValue(),
                                            (String) s.child("email").getValue(),
                                            (String) s.child("profileImage").getValue());
                                    }
                                    @Override public void onCancelled(DatabaseError e) {}
                                });
                        }
                    }
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    public void listenForStatusChange(String friendUID, StatusListener listener) {
        database.getReference("users").child(friendUID).child("status")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot s) {
                    String status = (String) s.getValue();
                    listener.onStatusChanged(status != null ? status : "offline");
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    public void setUserOnlineStatus(String uid, String status) {
        database.getReference("users").child(uid).child("status").setValueAsync(status);
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String encodeFile(File file, String messageType) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String b64   = Base64.getEncoder().encodeToString(bytes);
        String ext   = getExtension(file.getName());
        String mime  = getMime(ext, messageType);
        return "data:" + mime + ";base64," + b64;
    }

    private String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0) ? name.substring(i + 1).toLowerCase() : "bin";
    }

    private String getMime(String ext, String type) {
        switch (type) {
            case "audio": return "audio/" + (ext.equals("mp3") ? "mpeg" : ext);
            case "image": return "image/" + (ext.equals("jpg")  ? "jpeg" : ext);
            default:      return "application/octet-stream";
        }
    }

    // ─────────────────────────────────────────────
    // LISTENER INTERFACES
    // ─────────────────────────────────────────────

    public interface MessageListener {
        void onNewMessage(String fromUID, String message, long timestamp,
                          String type, String fileName);
    }
    public interface GroupMessageListener {
        void onNewGroupMessage(String fromUID, String senderName, String message,
                               long timestamp, String type, String fileName);
    }
    public interface UserSearchListener {
        void onUserFound(String uid, String name, String email, String profilePicData);
    }
    public interface GroupListener {
        void onGroupFound(String groupId, String groupName);
    }
    public interface StatusListener {
        void onStatusChanged(String status);
    }
}
