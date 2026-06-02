package com.WeConnect.services;

import java.util.HashMap;
import java.util.Map;

import com.WeConnect.services.listeners.StatusListener;
import com.WeConnect.services.listeners.UserSearchListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

/**
 * FriendService — handles the social graph between users.
 *
 * Responsibilities:
 *   - Search for users by name
 *   - Send / accept / decline friend requests
 *   - Load a user's friend list
 *   - Load incoming friend requests
 *   - Listen for a friend's online/offline status in real time
 *
 * Firebase structure:
 *   /friends/{uid}/{friendUID}             — undirected edge, value "friend"
 *   /friend_requests/{uid}/{otherUID}      — directed edge, value "sent" or "received"
 *   /users/{uid}                           — user profile (name, email, profileImage)
 *
 * DAA concepts (mention in viva):
 *   Directed Graph  — friend_requests are directed edges (A→B means A sent to B).
 *   Undirected Graph— accepting converts to undirected edges in /friends (A↔B).
 *   Linear Search   — searchUsers is O(n) over all users. A Trie would give O(k)
 *                     where k = query length, but requires additional DB structure.
 */
public class FriendService {

    private FriendService() {}

    // ─────────────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────────────

    /**
     * Searches all users by name (case-insensitive substring match).
     * O(n) linear scan — acceptable for current user count.
     * Skips the current user from results.
     *
     * Viva tip: mention that a Trie indexed on name characters would
     * reduce this to O(k) per query, where k = query length.
     */
    public static void searchUsers(String query, UserSearchListener listener) {
        FirebaseInitializer.getDatabase().child("users")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot u : snapshot.getChildren()) {
                        String name  = (String) u.child("name").getValue();
                        String uid   = (String) u.child("uid").getValue();
                        String email = (String) u.child("email").getValue();
                        String pic   = (String) u.child("profileImage").getValue();
                        if (name != null
                                && name.toLowerCase().contains(query.toLowerCase())
                                && !uid.equals(AuthService.currentUserUID))
                            listener.onUserFound(uid, name, email, pic);
                    }
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }

    // ─────────────────────────────────────────────
    // LOAD FRIENDS
    // ─────────────────────────────────────────────

    /**
     * Loads all accepted friends for the current user.
     * First reads /friends/{uid} for the list of friend UIDs,
     * then fetches each friend's profile from /users/{fid}.
     */
    public static void getAllFriends(UserSearchListener listener) {
        FirebaseInitializer.getDatabase()
            .child("friends").child(AuthService.currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot fs : snapshot.getChildren()) {
                        String fid = fs.getKey();
                        FirebaseInitializer.getDatabase()
                            .child("users").child(fid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot s) {
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

    // ─────────────────────────────────────────────
    // FRIEND REQUESTS
    // ─────────────────────────────────────────────

    /**
     * Sends a friend request from current user to receiverUID.
     * Writes directed edges in both directions:
     *   receiver sees value "sent"    (someone sent them a request)
     *   sender   sees value "received" (they sent a request out)
     */
    public static void sendFriendRequest(String receiverUID) {
        Map<String, Object> m = new HashMap<>();
        m.put("friend_requests/" + receiverUID          + "/" + AuthService.currentUserUID, "sent");
        m.put("friend_requests/" + AuthService.currentUserUID + "/" + receiverUID,          "received");
        FirebaseInitializer.getDatabase().updateChildrenAsync(m);
    }

    /**
     * Accepts a pending friend request from senderUID.
     * Removes both directed request edges and creates undirected friend edges.
     */
    public static void acceptFriendRequest(String senderUID) {
        Map<String, Object> m = new HashMap<>();
        // Remove request edges
        m.put("friend_requests/" + AuthService.currentUserUID + "/" + senderUID,          null);
        m.put("friend_requests/" + senderUID          + "/" + AuthService.currentUserUID, null);
        // Create undirected friend edges
        m.put("friends/" + AuthService.currentUserUID + "/" + senderUID,          "friend");
        m.put("friends/" + senderUID          + "/" + AuthService.currentUserUID, "friend");
        FirebaseInitializer.getDatabase().updateChildrenAsync(m);
    }

    /** Removes both directed request edges without creating a friendship. */
    public static void declineFriendRequest(String senderUID) {
        FirebaseInitializer.getDatabase()
            .child("friend_requests")
            .child(AuthService.currentUserUID).child(senderUID).removeValueAsync();
        FirebaseInitializer.getDatabase()
            .child("friend_requests")
            .child(senderUID).child(AuthService.currentUserUID).removeValueAsync();
    }

    // ─────────────────────────────────────────────
    // INCOMING REQUESTS
    // ─────────────────────────────────────────────

    /**
     * Loads all pending incoming friend requests for the current user.
     * A "sent" value means someone sent us a request (see sendFriendRequest above).
     */
    public static void getIncomingRequests(UserSearchListener listener) {
        FirebaseInitializer.getDatabase()
            .child("friend_requests").child(AuthService.currentUserUID)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot r : snapshot.getChildren()) {
                        if ("sent".equals(r.getValue())) {
                            String sid = r.getKey();
                            FirebaseInitializer.getDatabase()
                                .child("users").child(sid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot s) {
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

    // ─────────────────────────────────────────────
    // STATUS LISTENER
    // ─────────────────────────────────────────────

    /**
     * Attaches a real-time listener to /users/{friendUID}/status.
     * Fires immediately with the current status, then on every change.
     * Used to show "• online" / "• offline" in the chat header.
     */
    public static void listenForStatusChange(String friendUID, StatusListener listener) {
        FirebaseInitializer.getDatabase()
            .child("users").child(friendUID).child("status")
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot s) {
                    String status = (String) s.getValue();
                    listener.onStatusChanged(status != null ? status : "offline");
                }
                @Override public void onCancelled(DatabaseError e) {}
            });
    }
}