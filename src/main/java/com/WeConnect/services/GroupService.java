package com.WeConnect.services;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.WeConnect.services.listeners.GroupListener;
import com.WeConnect.services.listeners.GroupMessageListener;
import com.WeConnect.util.FileEncoder;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

/**
 * GroupService — handles all group chat operations.
 *
 * Responsibilities:
 *   - Create a group and register all members under /user_groups
 *   - Send text and file messages to a group
 *   - Listen for incoming group messages in real time
 *   - Load all groups the current user belongs to
 *
 * Firebase structure:
 *   /groups/{groupId}                    — group metadata (name, members, createdBy)
 *   /group_messages/{groupId}/{msgId}    — messages
 *   /user_groups/{uid}/{groupId}         — index for O(1) group lookup per user
 *   /group_last_messages/{groupId}       — preview for group list
 */
public class GroupService {

    private GroupService() {}

    // ─────────────────────────────────────────────
    // CREATE GROUP
    // ─────────────────────────────────────────────

    /**
     * Creates a group in /groups and registers all members (including creator)
     * in /user_groups so each member can find their groups in O(1).
     *
     * Returns the new groupId on success.
     */
    public static CompletableFuture<String> createGroup(String groupName,
                                                         List<String> memberUIDs) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            DatabaseReference gRef = FirebaseInitializer.getDatabase()
                .child("groups").push();
            String groupId = gRef.getKey();

            // Build members map — creator always included
            Map<String, Object> members = new HashMap<>();
            members.put(AuthService.currentUserUID, true);
            for (String uid : memberUIDs) members.put(uid, true);

            Map<String, Object> data = new HashMap<>();
            data.put("name",      groupName);
            data.put("createdBy", AuthService.currentUserUID);
            data.put("createdAt", System.currentTimeMillis());
            data.put("members",   members);
            gRef.setValueAsync(data);

            // Write membership index for every member including creator
            Map<String, Object> memberUpdates = new HashMap<>();
            memberUpdates.put("user_groups/" + AuthService.currentUserUID + "/" + groupId, groupName);
            for (String uid : memberUIDs)
                memberUpdates.put("user_groups/" + uid + "/" + groupId, groupName);
            FirebaseInitializer.getDatabase().updateChildrenAsync(memberUpdates);

            f.complete(groupId);
        } catch (Exception e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    // ─────────────────────────────────────────────
    // SEND — TEXT
    // ─────────────────────────────────────────────

    /**
     * Sends a message to a group. Includes senderName so receivers can
     * display who sent each message without an extra DB lookup.
     */
    public static void sendGroupMessage(String groupId, String content,
                                         String type, String fileName) {
        DatabaseReference ref = FirebaseInitializer.getDatabase()
            .child("group_messages").child(groupId).push();
        long ts = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("message",    content);
        body.put("type",       type != null ? type : "text");
        body.put("from",       AuthService.currentUserUID);
        body.put("senderName", AuthService.currentUserName);
        body.put("time",       ts);
        body.put("seen",       false);
        if (fileName != null) body.put("fileName", fileName);
        ref.setValueAsync(body);

        // Update last message preview
        Map<String, Object> last = new HashMap<>();
        last.put("text", "text".equals(type) ? content : "[" + type + "]");
        last.put("time", ts);
        FirebaseInitializer.getDatabase()
            .child("group_last_messages").child(groupId).setValueAsync(last);
    }

    // ─────────────────────────────────────────────
    // SEND — FILE / IMAGE / AUDIO
    // ─────────────────────────────────────────────

    /**
     * Encodes a file to Base64 on a background thread and sends it to the group.
     */
    public static CompletableFuture<Void> sendGroupFileMessage(String groupId,
                                                                File file,
                                                                String messageType) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        new Thread(() -> {
            try {
                String dataUri = FileEncoder.encode(file, messageType);
                sendGroupMessage(groupId, dataUri, messageType, file.getName());
                f.complete(null);
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        }).start();
        return f;
    }

    // ─────────────────────────────────────────────
    // LISTEN
    // ─────────────────────────────────────────────

    /**
     * Attaches a real-time listener to /group_messages/{groupId}.
     * Fires onChildAdded for all existing messages, then for each new one.
     */
    public static void listenForGroupMessages(String groupId,
                                               GroupMessageListener listener) {
        FirebaseInitializer.getDatabase()
            .child("group_messages").child(groupId)
            .addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot s, String prev) {
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

    // ─────────────────────────────────────────────
    // LOAD USER'S GROUPS
    // ─────────────────────────────────────────────

    /**
     * Reads /user_groups/{currentUID} and fires the listener once per group.
     * The /user_groups index means this is O(g) where g = number of user's groups,
     * not O(all groups) — a deliberate design choice for scalability.
     */
    public static void getUserGroups(GroupListener listener) {
        FirebaseInitializer.getDatabase()
            .child("user_groups").child(AuthService.currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot s) {
                    for (DataSnapshot g : s.getChildren())
                        listener.onGroupFound(g.getKey(), (String) g.getValue());
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }
}