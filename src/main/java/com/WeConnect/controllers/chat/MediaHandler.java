package com.WeConnect.controllers.chat;

import java.io.File;
import java.util.Base64;

import com.WeConnect.audio.AudioRecorder;
import com.WeConnect.services.ChatService;
import com.WeConnect.services.GroupService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * MediaHandler — handles all non-text media operations in a chat:
 *   - File/image/audio sending (via FileChooser)
 *   - Audio recording (via AudioRecorder)
 *   - Audio playback (javax.sound.sampled, no JavaFX Media needed)
 *   - Full-size image viewer popup
 *   - File viewer popup
 *   - Save file to disk
 *
 * Needs ChatContext to know whether a 1-on-1 or group chat is active,
 * and a Window reference for FileChooser dialogs.
 */
public class MediaHandler {

    private final ChatContext ctx;
    private final Window      ownerWindow;

    public MediaHandler(ChatContext ctx, Window ownerWindow) {
        this.ctx         = ctx;
        this.ownerWindow = ownerWindow;
    }

    // ─────────────────────────────────────────────
    // SEND FILE
    // ─────────────────────────────────────────────

    /**
     * Opens a FileChooser, detects the file type, and sends it to the active chat.
     * Runs encoding on a background thread — UI stays responsive.
     */
    public void handleSendFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Send File (max 500 KB)");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images",    "*.jpg","*.jpeg","*.png","*.gif","*.webp"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf","*.docx","*.txt","*.xlsx","*.zip"),
            new FileChooser.ExtensionFilter("Audio",     "*.mp3","*.wav","*.ogg","*.m4a"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fc.showOpenDialog(ownerWindow);
        if (file == null) return;

        String type = detectType(file.getName());

        if (ctx.activeFriend != null) {
            ChatService.sendFileMessage(ctx.activeFriend.getUid(), file, type)
                .exceptionally(e -> {
                    Platform.runLater(() -> showAlert(e.getMessage()));
                    return null;
                });
        } else if (ctx.activeGroupId != null) {
            GroupService.sendGroupFileMessage(ctx.activeGroupId, file, type)
                .exceptionally(e -> {
                    Platform.runLater(() -> showAlert(e.getMessage()));
                    return null;
                });
        }
    }

    // ─────────────────────────────────────────────
    // RECORD AUDIO
    // ─────────────────────────────────────────────

    /**
     * Shows a modal recording dialog.
     * Uses AudioRecorder (javax.sound.sampled) to capture mic input to a WAV file,
     * then sends it as an "audio" message when the user stops recording.
     */
    public void handleRecordAudio() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Voice Message");

        Label  status  = new Label("Press Record to start");
        status.setStyle("-fx-text-fill:#a8a8c0;-fx-font-size:13px;");

        Button recBtn  = new Button("🎙  Record");
        Button stopBtn = new Button("⏹  Stop & Send");
        stopBtn.setDisable(true);

        recBtn.setStyle("-fx-background-color:#7c6af7;-fx-text-fill:white;"
            + "-fx-background-radius:8;-fx-padding:10 22;-fx-font-size:13px;");
        stopBtn.setStyle("-fx-background-color:#ef4444;-fx-text-fill:white;"
            + "-fx-background-radius:8;-fx-padding:10 22;-fx-font-size:13px;");

        AudioRecorder recorder = new AudioRecorder();
        File[] outFile = {null};

        recBtn.setOnAction(e -> {
            try {
                outFile[0] = File.createTempFile("voice_", ".wav");
                recorder.start(outFile[0]);
                status.setText("Recording... speak now 🔴");
                recBtn.setDisable(true);
                stopBtn.setDisable(false);
            } catch (Exception ex) {
                status.setText("Error: " + ex.getMessage());
            }
        });

        stopBtn.setOnAction(e -> {
            recorder.stop();
            stage.close();
            if (outFile[0] != null && outFile[0].exists()) {
                if (ctx.activeFriend != null) {
                    ChatService.sendFileMessage(ctx.activeFriend.getUid(), outFile[0], "audio");
                } else if (ctx.activeGroupId != null) {
                    GroupService.sendGroupFileMessage(ctx.activeGroupId, outFile[0], "audio");
                }
            }
        });

        VBox layout = new VBox(16, status, recBtn, stopBtn);
        layout.setPadding(new Insets(24));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color:#1a1a24;");
        stage.setScene(new Scene(layout, 280, 180));
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────
    // PLAY AUDIO
    // ─────────────────────────────────────────────

    /**
     * Decodes a Base64 audio data URI, writes it to a temp WAV file,
     * and plays it using javax.sound.sampled on a background thread.
     * Updates the play button text while playing.
     */
    public void playAudioFromBase64(String dataUri, Button playBtn) {
        new Thread(() -> {
            try {
                String b64   = dataUri.contains(",") ? dataUri.split(",", 2)[1] : dataUri;
                byte[] bytes = Base64.getDecoder().decode(b64);
                File   tmp   = File.createTempFile("play_", ".wav");
                tmp.deleteOnExit();
                java.nio.file.Files.write(tmp.toPath(), bytes);

                javax.sound.sampled.AudioInputStream ais =
                    javax.sound.sampled.AudioSystem.getAudioInputStream(tmp);
                javax.sound.sampled.Clip clip =
                    javax.sound.sampled.AudioSystem.getClip();
                clip.open(ais);

                Platform.runLater(() -> playBtn.setText("▶  Playing..."));
                clip.start();

                clip.addLineListener(event -> {
                    if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                        clip.close();
                        Platform.runLater(() -> playBtn.setText("▶  Play Voice"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Audio error: " + e.getMessage()));
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    // IMAGE VIEWER
    // ─────────────────────────────────────────────

    /** Opens a modal popup showing the full-size image with a save button. */
    public void showImageViewer(String dataUri) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Image");
        stage.setResizable(true);

        Image img = MessageRenderer.decodeBase64Image(dataUri);
        if (img == null) { showAlert("Could not load image."); return; }

        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(500);
        iv.setFitHeight(500);

        Button saveBtn  = styledButton("💾  Save Image", "#7c6af7");
        Button closeBtn = styledButton("Close",           "#2a2a3e");
        saveBtn.setOnAction(e  -> saveFile(dataUri, "image"));
        closeBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(12, saveBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER);

        ScrollPane sp = new ScrollPane(iv);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color:#1a1a24;-fx-border-color:transparent;");

        VBox layout = new VBox(16, sp, buttons);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(16));
        layout.setStyle("-fx-background-color:#1a1a24;");

        stage.setScene(new Scene(layout, 540, 580));
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────
    // FILE VIEWER
    // ─────────────────────────────────────────────

    /** Opens a modal popup showing the filename with a save button. */
    public void showFileViewer(String dataUri, String fileName) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("File: " + fileName);
        stage.setResizable(false);

        Label icon    = new Label("📄");
        icon.setStyle("-fx-font-size:48px;");

        Label nameLbl = new Label(fileName);
        nameLbl.setStyle("-fx-text-fill:#e0e0f0;-fx-font-size:14px;-fx-font-weight:bold;");
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(300);

        Button saveBtn  = styledButton("💾  Save File", "#7c6af7");
        Button closeBtn = styledButton("Close",          "#2a2a3e");
        saveBtn.setOnAction(e  -> saveFile(dataUri, fileName));
        closeBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(12, saveBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(16, icon, nameLbl, buttons);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(32));
        layout.setStyle("-fx-background-color:#1a1a24;");

        stage.setScene(new Scene(layout, 380, 260));
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────
    // SAVE FILE TO DISK
    // ─────────────────────────────────────────────

    /** Decodes Base64 data URI and writes bytes to a user-chosen location. */
    public void saveFile(String dataUri, String suggestedName) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save File");
        fc.setInitialFileName(suggestedName.equals("image") ? "image.png" : suggestedName);
        File dest = fc.showSaveDialog(ownerWindow);
        if (dest == null) return;

        new Thread(() -> {
            try {
                String b64   = dataUri.contains(",") ? dataUri.split(",", 2)[1] : dataUri;
                byte[] bytes = Base64.getDecoder().decode(b64);
                java.nio.file.Files.write(dest.toPath(), bytes);
                Platform.runLater(() -> showAlert("Saved to: " + dest.getAbsolutePath()));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Save failed: " + e.getMessage()));
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    /** Returns "image", "audio", or "file" based on the file extension. */
    public static String detectType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".webp")) return "image";
        if (n.endsWith(".mp3") || n.endsWith(".wav")
                || n.endsWith(".ogg") || n.endsWith(".m4a"))  return "audio";
        return "file";
    }

    private Button styledButton(String text, String bgColor) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + bgColor + ";-fx-text-fill:white;"
            + "-fx-background-radius:8;-fx-padding:9 20;-fx-font-size:13px;");
        return b;
    }

    private void showAlert(String msg) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION, msg,
            javafx.scene.control.ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}