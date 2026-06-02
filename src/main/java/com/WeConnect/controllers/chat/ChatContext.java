package com.WeConnect.controllers.chat;

import java.util.HashMap;
import java.util.Map;

import com.WeConnect.models.User;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * ChatContext — plain shared-state holder for the active chat session.
 *
 * Passed into ChatPanelController, MessageRenderer, and MediaHandler
 * so they all read/write the same state without being coupled to each other
 * or to DashboardController directly.
 *
 * Think of it as the "what is currently open" object:
 *   - activeFriend     → 1-on-1 chat target (null if group is open)
 *   - activeGroupId    → group chat target   (null if 1-on-1 is open)
 *   - bubbleMap        → HashMap for O(1) tick updates on rendered messages
 *   - UI references    → messagesBox, chatScrollPane, messageInput
 */
public class ChatContext {

    // ── Active chat target ────────────────────────────────────────────────────
    public User   activeFriend    = null;
    public String activeGroupId   = null;
    public String activeGroupName = null;

    // ── Firebase listener — stored so we can remove it when switching chats ──
    public ChildEventListener activeMessageListener = null;
    public DatabaseReference  activeMessageRef      = null;

    /**
     * bubbleMap: messageKey → rendered HBox row
     * Allows O(1) lookup when the "seen" tick needs to update
     * without re-scanning the entire messagesBox children list.
     *
     * DAA note (viva): HashMap average O(1) insert and lookup.
     */
    public final Map<String, javafx.scene.layout.HBox> bubbleMap = new HashMap<>();

    // ── JavaFX UI nodes shared across sub-controllers ─────────────────────────
    public VBox       messagesBox;
    public ScrollPane chatScrollPane;
    public TextField  messageInput;

    /** Clears active chat state when switching to a new conversation. */
    public void reset() {
        activeFriend    = null;
        activeGroupId   = null;
        activeGroupName = null;
        bubbleMap.clear();
    }
}
