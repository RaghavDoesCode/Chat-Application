package com.WeConnect.controllers.profile;

import java.io.File;

import com.WeConnect.controllers.chat.MessageRenderer;
import com.WeConnect.services.AuthService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * ProfileController — handles everything related to the current user's avatar.
 *
 * Responsibilities:
 *   - Decode and display the current user's profile picture in the sidebar
 *   - Handle avatar click: show viewer if pic exists, else go straight to picker
 *   - Show a full-size profile picture viewer popup with a change option
 *   - Open FileChooser, upload selected image via AuthService, refresh avatar
 *
 * Uses AuthService.uploadProfilePicture() which runs encoding on a background
 * thread and returns a CompletableFuture<String> (the new data URI).
 */
public class ProfileController {

    private final ImageView currentUserAvatar;
    private final Label     defaultAvatarLabel;
    private final Window    ownerWindow;

    public ProfileController(ImageView currentUserAvatar,
                              Label defaultAvatarLabel,
                              Window ownerWindow) {
        this.currentUserAvatar  = currentUserAvatar;
        this.defaultAvatarLabel = defaultAvatarLabel;
        this.ownerWindow        = ownerWindow;
    }

    // ─────────────────────────────────────────────
    // LOAD AVATAR
    // ─────────────────────────────────────────────

    /**
     * Decodes and displays the given Base64 data URI as the user's avatar.
     * Hides the default 👤 emoji label when a real picture is shown.
     * Safe to call with null — does nothing.
     */
    public void loadMyAvatar(String dataUri) {
        if (dataUri == null || dataUri.isEmpty()) return;
        Image img = MessageRenderer.decodeBase64Image(dataUri);
        if (img == null) return;

        currentUserAvatar.setImage(img);
        currentUserAvatar.setFitWidth(36);
        currentUserAvatar.setFitHeight(36);
        currentUserAvatar.setPreserveRatio(false);
        currentUserAvatar.setClip(new Circle(18, 18, 18));
        defaultAvatarLabel.setVisible(false);
    }

    // ─────────────────────────────────────────────
    // AVATAR CLICK
    // ─────────────────────────────────────────────

    /**
     * Called when the user clicks their avatar in the sidebar.
     * If no profile picture is set → opens file picker directly.
     * If a picture exists → shows the viewer with a change option.
     */
    public void handleAvatarClick() {
        if (AuthService.currentUserProfileBase64 == null) {
            pickAndUploadProfilePicture();
        } else {
            showProfilePictureViewer();
        }
    }

    // ─────────────────────────────────────────────
    // VIEWER POPUP
    // ─────────────────────────────────────────────

    /** Shows the current profile picture full-size with a change button. */
    private void showProfilePictureViewer() {
        Stage viewerStage = new Stage();
        viewerStage.initModality(Modality.APPLICATION_MODAL);
        viewerStage.setTitle(AuthService.currentUserName + "'s Profile Picture");
        viewerStage.setResizable(false);

        ImageView fullImage = new ImageView(currentUserAvatar.getImage());
        fullImage.setFitWidth(300);
        fullImage.setFitHeight(300);
        fullImage.setPreserveRatio(true);

        Button changeBtn = styledButton("📷  Change Profile Picture", "#7c6af7");
        Button closeBtn  = styledButton("Close",                       "#2a2a3e");

        changeBtn.setOnAction(e -> { viewerStage.close(); pickAndUploadProfilePicture(); });
        closeBtn.setOnAction(e  -> viewerStage.close());

        HBox buttons = new HBox(12, changeBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(16, fullImage, buttons);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(24));
        layout.setStyle("-fx-background-color:#1a1a24;");

        viewerStage.setScene(new Scene(layout));
        viewerStage.showAndWait();
    }

    // ─────────────────────────────────────────────
    // PICK AND UPLOAD
    // ─────────────────────────────────────────────

    /**
     * Opens a FileChooser filtered to image types, uploads the selected file
     * via AuthService (Base64 encoded into Firebase Realtime DB), and
     * reloads the avatar on success.
     */
    private void pickAndUploadProfilePicture() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Profile Picture (max 500 KB)");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = fc.showOpenDialog(ownerWindow);
        if (file == null) return;

        AuthService.uploadProfilePicture(file)
            .thenAccept(dataUri -> Platform.runLater(() -> {
                loadMyAvatar(dataUri);
                showAlert("Profile picture updated!");
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("Upload failed: " + e.getMessage()));
                return null;
            });
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private Button styledButton(String text, String bgColor) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + bgColor + ";-fx-text-fill:white;"
            + "-fx-background-radius:8;-fx-padding:9 20;-fx-font-size:13px;");
        return b;
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}