package com.WeConnect.controllers.chat;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * MessageRenderer — responsible for turning raw message data into
 * JavaFX UI nodes and adding them to the chat window.
 *
 * Handles all four message types:
 *   "text"  → styled Label bubble
 *   "image" → ImageView inside a bubble, tap to view full size
 *   "audio" → play button + filename label
 *   "file"  → filename label, tap to save
 *
 * Also handles read receipt tick updates (✓ grey → ✓✓ purple).
 *
 * Needs a ChatContext to know where to add bubbles (messagesBox)
 * and to store rendered rows in bubbleMap for O(1) tick updates.
 */
public class MessageRenderer {

    private final ChatContext     ctx;
    private final MediaHandler    mediaHandler;

    public MessageRenderer(ChatContext ctx, MediaHandler mediaHandler) {
        this.ctx          = ctx;
        this.mediaHandler = mediaHandler;
    }

    // ─────────────────────────────────────────────
    // ADD BUBBLE
    // ─────────────────────────────────────────────

    /**
     * Renders a single message as a bubble row and appends it to messagesBox.
     * Returns the HBox row so the caller can store it in bubbleMap.
     *
     * @param key        Firebase message key (used as bubbleMap key)
     * @param content    text content or Base64 data URI
     * @param mine       true if sent by current user
     * @param time       formatted time string e.g. "03:45 PM"
     * @param type       "text" | "image" | "audio" | "file"
     * @param fileName   original filename for file/audio messages
     * @param senderName non-null for group messages — shown above the bubble
     * @param seenStatus null = don't show tick; Boolean = show tick with state
     */
    public HBox addBubble(String key, String content, boolean mine, String time,
                           String type, String fileName, String senderName,
                           Boolean seenStatus) {
        HBox row = new HBox();
        row.setId("msg_" + key);
        row.setPadding(new Insets(3, 12, 3, 12));
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox msgBox = new VBox(3);
        msgBox.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Group sender name label (shown above bubble in group chats)
        if (senderName != null) {
            Label nl = new Label(senderName);
            nl.setStyle("-fx-text-fill:#a898ff;-fx-font-size:10px;-fx-padding:0 4 1 4;");
            msgBox.getChildren().add(nl);
        }

        // Render content based on type
        switch (type) {
            case "image" -> renderImage(content, mine, msgBox);
            case "audio" -> renderAudio(content, fileName, mine, msgBox);
            case "file"  -> renderFile(content, fileName, mine, msgBox);
            default      -> msgBox.getChildren().add(textBubble(content, mine));
        }

        // Time + tick row
        HBox timeRow = new HBox(4);
        timeRow.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        timeRow.getChildren().add(makeTimeLabel(time));

        if (mine && seenStatus != null) {
            timeRow.getChildren().add(makeTickLabel(seenStatus));
        }

        msgBox.getChildren().add(timeRow);
        row.getChildren().add(msgBox);
        ctx.messagesBox.getChildren().add(row);
        return row;
    }

    // ─────────────────────────────────────────────
    // TICK UPDATER
    // ─────────────────────────────────────────────

    /**
     * Finds the tick label inside an already-rendered bubble row and updates it.
     * Uses the row reference from bubbleMap — O(1) lookup, no list scan needed.
     *
     * Grey ✓ = sent, Purple ✓✓ = seen.
     */
    public void updateTickInBubble(HBox row, boolean seen, boolean mine) {
        if (!mine) return;
        VBox msgBox  = (VBox) row.getChildren().get(0);
        HBox timeRow = (HBox) msgBox.getChildren().get(msgBox.getChildren().size() - 1);
        if (timeRow.getChildren().size() >= 2) {
            Label tick = (Label) timeRow.getChildren().get(1);
            tick.setText(seen ? "✓✓" : "✓");
            tick.setStyle(seen
                ? "-fx-text-fill:#7c6af7;-fx-font-size:10px;"
                : "-fx-text-fill:#888888;-fx-font-size:10px;");
        }
    }

    // ─────────────────────────────────────────────
    // TYPE RENDERERS  (private helpers)
    // ─────────────────────────────────────────────

    private void renderImage(String content, boolean mine, VBox msgBox) {
        Image img = decodeBase64Image(content);
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(220);
            iv.setPreserveRatio(true);
            VBox imgBubble = new VBox(iv);
            imgBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
            imgBubble.setPadding(new Insets(6));
            imgBubble.setStyle("-fx-cursor:hand;");
            imgBubble.setOnMouseClicked(e -> mediaHandler.showImageViewer(content));
            msgBox.getChildren().add(imgBubble);
        } else {
            msgBox.getChildren().add(textBubble("[Image]", mine));
        }
    }

    private void renderAudio(String content, String fileName, boolean mine, VBox msgBox) {
        Button playBtn = new Button("▶  Play Voice");
        playBtn.getStyleClass().add(mine ? "audio-btn-mine" : "audio-btn-theirs");

        Label fileLabel = new Label(fileName != null ? fileName : "Voice Message");
        fileLabel.setStyle("-fx-text-fill:#888;-fx-font-size:11px;");

        HBox audioRow = new HBox(10, playBtn, fileLabel);
        audioRow.setAlignment(Pos.CENTER_LEFT);
        playBtn.setOnAction(e -> mediaHandler.playAudioFromBase64(content, playBtn));

        VBox audioBubble = new VBox(audioRow);
        audioBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
        audioBubble.setPadding(new Insets(10, 14, 10, 14));
        msgBox.getChildren().add(audioBubble);
    }

    private void renderFile(String content, String fileName, boolean mine, VBox msgBox) {
        String display = fileName != null ? fileName : "File";
        Label fileLbl  = new Label("📎 " + display);
        fileLbl.setWrapText(true);
        fileLbl.setMaxWidth(260);

        Label hint = new Label("tap to save");
        hint.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");

        VBox fileBubble = new VBox(4, fileLbl, hint);
        fileBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
        fileBubble.setPadding(new Insets(8, 14, 8, 14));
        fileBubble.setStyle("-fx-cursor:hand;");
        fileBubble.setOnMouseClicked(e -> mediaHandler.showFileViewer(content, display));
        msgBox.getChildren().add(fileBubble);
    }

    // ─────────────────────────────────────────────
    // SMALL UI HELPERS
    // ─────────────────────────────────────────────

    /** Creates a styled text bubble Label. */
    public Label textBubble(String text, boolean mine) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(340);
        l.setPadding(new Insets(10, 14, 10, 14));
        l.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
        return l;
    }

    private Label makeTimeLabel(String time) {
        Label l = new Label(time);
        l.getStyleClass().add("time-label");
        return l;
    }

    private Label makeTickLabel(boolean seen) {
        Label tick = new Label(seen ? "✓✓" : "✓");
        tick.setStyle(seen
            ? "-fx-text-fill:#7c6af7;-fx-font-size:10px;"
            : "-fx-text-fill:#888888;-fx-font-size:10px;");
        return tick;
    }

    // ─────────────────────────────────────────────
    // BASE64 IMAGE DECODE
    // ─────────────────────────────────────────────

    /** Decodes a Base64 data URI into a JavaFX Image. Returns null on failure. */
    public static Image decodeBase64Image(String dataUri) {
        try {
            String b64   = dataUri.contains(",") ? dataUri.split(",", 2)[1] : dataUri;
            byte[] bytes = Base64.getDecoder().decode(b64);
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}