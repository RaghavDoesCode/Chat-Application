package com.WeConnect.services;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.WeConnect.services.listeners.MessageListener;
import com.WeConnect.util.FileEncoder;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

/**
 * ChatService — handles all 1-on-1 messaging between two users.
 *
 * Responsibilities:
 *   - Send text messages
 *   - Send file / image / audio messages (Base64-encoded)
 *   - Listen for incoming messages in real time
 *   - Mark received messages as seen
 *
 * DAA concepts (mention in viva):
 *   Fan-out write  — dual-write to both sender and receiver paths so each user
 *                    can read their own messages in O(1) without scanning everyone.
 *                    Classic Space-Time Tradeoff: use more DB space to save read time.
 *
 *   HashMap        — message body built in O(1) average per field (k fields → O(k)).
 */
public class ChatService {

    private ChatService() {}

    // ─────────────────────────────────────────────
    // SEND — TEXT
    // ─────────────────────────────────────────────

    /** Sends a plain text message to receiverUID. */
    public static void sendMessage(String receiverUID, String text) {
        sendInternal(receiverUID, text, "text", null);
    }

    // ─────────────────────────────────────────────
    // SEND — FILE / IMAGE / AUDIO
    // ─────────────────────────────────────────────

    /**
     * Encodes a file to a Base64 data URI on a background thread, then sends it.
     * messageType: "image" | "audio" | "file"
     *
     * Returns a CompletableFuture so the caller can attach .exceptionally()
     * to show an error alert if the file is too large or unreadable.
     */
    public static CompletableFuture<Void> sendFileMessage(String receiverUID,
                                                           File file,
                                                           String messageType) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                String dataUri = FileEncoder.encode(file, messageType);
                sendInternal(receiverUID, dataUri, messageType, file.getName());
                f.complete(null);
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        }).start();
        return f;
    }

    // ─────────────────────────────────────────────
    // SEND — INTERNAL (fan-out write)
    // ─────────────────────────────────────────────

    /**
     * Core send method. Builds the message body, then does a fan-out write:
     * writes the same message to both the sender's and receiver's message paths.
     * Also updates the last-message preview for both users.
     */
    private static void sendInternal(String receiverUID, String content,
                                      String type, String fileName) {
        DatabaseReference ref = FirebaseInitializer.getDatabase()
            .child("messages")
            .child(AuthService.currentUserUID)
            .child(receiverUID)
            .push();
        String msgId = ref.getKey();
        long   ts    = System.currentTimeMillis();

        // Build message body — HashMap gives O(1) average per put
        Map<String, Object> body = new HashMap<>();
        body.put("message",  content);
        body.put("type",     type);
        body.put("from",     AuthService.currentUserUID);
        body.put("to",       receiverUID);
        body.put("seen",     false);
        body.put("time",     ts);
        if (fileName != null) body.put("fileName", fileName);

        // Fan-out write — both paths updated atomically in one updateChildren call
        Map<String, Object> fanOut = new HashMap<>();
        String senderPath   = "messages/" + AuthService.currentUserUID + "/" + receiverUID + "/" + msgId;
        String receiverPath = "messages/" + receiverUID + "/" + AuthService.currentUserUID + "/" + msgId;
        fanOut.put(senderPath,   body);
        fanOut.put(receiverPath, body);

        // Last-message preview (shown in friends list)
        Map<String, Object> last = new HashMap<>();
        last.put("text", type.equals("text") ? content : "[" + type + "]");
        last.put("time", ts);
        fanOut.put("last_messages/" + AuthService.currentUserUID + "/" + receiverUID, last);
        fanOut.put("last_messages/" + receiverUID + "/" + AuthService.currentUserUID, last);

        FirebaseInitializer.getDatabase().updateChildrenAsync(fanOut);
    }

    // ─────────────────────────────────────────────
    // LISTEN
    // ─────────────────────────────────────────────

    /**
     * Attaches a real-time listener to the current user's message path with friendUID.
     *
     * - onChildAdded fires for every existing message on attach, then for new ones.
     * - onChildChanged fires when the "seen" field flips to true (read receipt).
     *
     * Returns the ChildEventListener reference so the caller can remove it
     * when the user switches chats (prevents duplicate message rendering).
     */
    public static ChildEventListener listenForMessages(String friendUID,
                                                        MessageListener listener) {
        DatabaseReference ref = FirebaseInitializer.getDatabase()
            .child("messages")
            .child(AuthService.currentUserUID)
            .child(friendUID);

        ChildEventListener cel = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot s, String prev) { emit(s); }

            @Override
            public void onChildChanged(DataSnapshot s, String prev) { emit(s); }

            private void emit(DataSnapshot s) {
                String key      = s.getKey();
                String text     = (String)  s.child("message").getValue();
                String from     = (String)  s.child("from").getValue();
                String type     = (String)  s.child("type").getValue();
                String file     = (String)  s.child("fileName").getValue();
                long   time     = s.child("time").getValue(Long.class);
                Boolean seenVal = s.child("seen").getValue(Boolean.class);
                boolean seen    = seenVal != null && seenVal;
                listener.onNewMessage(key, from, text, time,
                    type != null ? type : "text", file, seen);
            }

            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        };

        ref.addChildEventListener(cel);
        return cel;
    }

    // ─────────────────────────────────────────────
    // MARK SEEN
    // ─────────────────────────────────────────────

    /**
     * Flips seen=true for all unread messages from friendUID in both fan-out paths.
     * Called when the current user opens a chat window.
     */
    public static void markMessagesAsSeen(String friendUID) {
        FirebaseInitializer.getDatabase()
            .child("messages")
            .child(AuthService.currentUserUID)
            .child(friendUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot msg : snapshot.getChildren()) {
                        String from = (String) msg.child("from").getValue();
                        if (friendUID.equals(from)) {
                            // Mark seen in both paths
                            msg.getRef().child("seen").setValueAsync(true);
                            FirebaseInitializer.getDatabase()
                                .child("messages")
                                .child(friendUID)
                                .child(AuthService.currentUserUID)
                                .child(msg.getKey())
                                .child("seen").setValueAsync(true);
                        }
                    }
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }
}