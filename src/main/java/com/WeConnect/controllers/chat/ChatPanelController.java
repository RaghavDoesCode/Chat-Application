package com.WeConnect.controllers.chat;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.WeConnect.models.User;
import com.WeConnect.services.AuthService;
import com.WeConnect.services.ChatService;
import com.WeConnect.services.FriendService;
import com.WeConnect.services.GroupService;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

/**
 * ChatPanelController — manages the right-hand chat panel for 1-on-1 chats.
 *
 * Responsibilities:
 *   - Open a chat with a friend (attach listener, set header, clear old messages)
 *   - Send text messages
 *   - Remove the old Firebase listener when switching chats (prevents duplicates)
 *   - Trigger markMessagesAsSeen when a chat is opened
 *
 * Works with:
 *   ChatContext     — shared state (activeFriend, bubbleMap, UI nodes)
 *   MessageRenderer — turns raw message data into bubble UI nodes
 *   FriendService   — status listener for online/offline header
 *   ChatService     — send + listen
 */
public class ChatPanelController {

    private final ChatContext      ctx;
    private final MessageRenderer  renderer;

    // UI references injected by DashboardController
    private final Label     chatHeaderName;
    private final Label     chatHeaderStatus;
    private final ImageView chatHeaderAvatar;
    private final javafx.scene.layout.VBox  chatPane;
    private final javafx.scene.layout.VBox  welcomePane;

    public ChatPanelController(ChatContext ctx,
                                MessageRenderer renderer,
                                Label chatHeaderName,
                                Label chatHeaderStatus,
                                ImageView chatHeaderAvatar,
                                javafx.scene.layout.VBox chatPane,
                                javafx.scene.layout.VBox welcomePane) {
        this.ctx              = ctx;
        this.renderer         = renderer;
        this.chatHeaderName   = chatHeaderName;
        this.chatHeaderStatus = chatHeaderStatus;
        this.chatHeaderAvatar = chatHeaderAvatar;
        this.chatPane         = chatPane;
        this.welcomePane      = welcomePane;
    }

    // ─────────────────────────────────────────────
    // OPEN CHAT
    // ─────────────────────────────────────────────

    /**
     * Opens a 1-on-1 chat with the given user.
     *
     * Steps:
     *   1. Remove the previous Firebase listener to prevent duplicate bubbles
     *   2. Reset shared state
     *   3. Set chat header (name, avatar, status)
     *   4. Attach a new message listener
     *   5. Mark all incoming messages as seen
     */
    public void openChat(User user) {
        // Step 1 — remove old listener
        detachCurrentListener();

        // Step 2 — reset state
        ctx.reset();
        ctx.activeFriend = user;
        ctx.messagesBox.getChildren().clear();

        // Step 3 — update header
        chatHeaderName.setText(user.getName());
        chatHeaderStatus.setText("• offline");
        welcomePane.setVisible(false);
        chatPane.setVisible(true);
        setFriendAvatar(user.getProfileImage());

        // Live status listener
        FriendService.listenForStatusChange(user.getUid(),
            status -> Platform.runLater(
                () -> chatHeaderStatus.setText("• " + status)));

        // Step 4 — store ref for future cleanup, attach listener
        ctx.activeMessageRef = com.WeConnect.services.FirebaseInitializer
            .getDatabase()
            .child("messages")
            .child(AuthService.currentUserUID)
            .child(user.getUid());

        ctx.activeMessageListener = ChatService.listenForMessages(user.getUid(),
            (key, from, text, time, type, file, seen) ->
                Platform.runLater(() -> {
                    boolean mine = from.equals(AuthService.currentUserUID);
                    if (ctx.bubbleMap.containsKey(key)) {
                        // Already rendered — just update the tick
                        renderer.updateTickInBubble(ctx.bubbleMap.get(key), seen, mine);
                    } else {
                        // New message — render and store in bubbleMap for O(1) future updates
                        HBox row = renderer.addBubble(key, text, mine,
                            formatTime(time), type, file, null,
                            mine ? seen : null);
                        ctx.bubbleMap.put(key, row);
                        scrollToBottom();
                    }
                })
        );

        // Step 5 — mark seen
        ChatService.markMessagesAsSeen(user.getUid());
    }

    // ─────────────────────────────────────────────
    // SEND MESSAGE
    // ─────────────────────────────────────────────

    /**
     * Reads messageInput, sends to the active chat (1-on-1 or group), clears input.
     * Called from DashboardController's @FXML handleSendMessage.
     */
    public void handleSendMessage() {
        String text = ctx.messageInput.getText().trim();
        if (text.isEmpty()) return;

        if (ctx.activeFriend != null) {
            ChatService.sendMessage(ctx.activeFriend.getUid(), text);
        } else if (ctx.activeGroupId != null) {
            GroupService.sendGroupMessage(ctx.activeGroupId, text, "text", null);
        }

        ctx.messageInput.clear();
    }

    // ─────────────────────────────────────────────
    // CLEANUP
    // ─────────────────────────────────────────────

    /** Removes the active Firebase listener. Call before opening a new chat. */
    public void detachCurrentListener() {
        if (ctx.activeMessageRef != null && ctx.activeMessageListener != null) {
            ctx.activeMessageRef.removeEventListener(ctx.activeMessageListener);
            ctx.activeMessageListener = null;
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private void setFriendAvatar(String pic) {
        if (pic != null && !pic.equals("default") && pic.startsWith("data:")) {
            Image img = MessageRenderer.decodeBase64Image(pic);
            if (img != null) {
                chatHeaderAvatar.setImage(img);
                chatHeaderAvatar.setClip(new Circle(19, 19, 19));
                return;
            }
        }
        chatHeaderAvatar.setImage(null);
    }

    private void scrollToBottom() {
        ctx.chatScrollPane.setVvalue(1.0);
    }

    private String formatTime(long ts) {
        return new SimpleDateFormat("hh:mm a").format(new Date(ts));
    }
}