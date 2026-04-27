package com.WeConnect.controllers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.WeConnect.models.User;
import com.WeConnect.services.FirebaseService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DashboardController {

    // ── Left panel ───────────────────────────────
    @FXML private TabPane      leftTabPane;
    @FXML private ListView<User>   friendsListView;
    @FXML private ListView<User>   requestsListView;
    @FXML private ListView<User>   searchListView;
    @FXML private ListView<String> groupsListView;
    @FXML private TextField    searchField;
    @FXML private Label        currentUserNameLabel;
    @FXML private ImageView    currentUserAvatar;

    // ── Right panel ──────────────────────────────
    @FXML private VBox       chatPane;
    @FXML private Label      chatHeaderName;
    @FXML private Label      chatHeaderStatus;
    @FXML private ImageView  chatHeaderAvatar;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox       messagesBox;
    @FXML private TextField  messageInput;
    @FXML private Button     sendBtn;
    @FXML private VBox       welcomePane;

    // State
    private User   activeFriend = null;
    private String activeGroupId   = null;
    private String activeGroupName = null;

    private final List<User>   friendsList  = new ArrayList<>();
    private final List<String> groupIdList  = new ArrayList<>();

    private static final String[] EMOJIS = {
        "😀","😂","😍","😎","😢","😡","👍","👎","❤️","🔥",
        "🎉","😊","🤔","😴","🥳","👏","🙌","💯","✅","🚀",
        "😇","🤩","🥺","😤","🤯","🙏","💪","✌️","🫡","💬"
    };

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────

    @FXML
    public void initialize() {
        welcomePane.setVisible(true);
        chatPane.setVisible(false);

        currentUserNameLabel.setText(FirebaseService.currentUserName);
        loadMyAvatar(FirebaseService.currentUserProfileBase64);

        loadFriends();
        loadIncomingRequests();
        loadGroups();

        friendsListView.setOnMouseClicked(e -> {
            User u = friendsListView.getSelectionModel().getSelectedItem();
            if (u != null) openChat(u);
        });

        groupsListView.setOnMouseClicked(e -> {
            int i = groupsListView.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < groupIdList.size())
                openGroupChat(groupIdList.get(i),
                    groupsListView.getItems().get(i).replace("👥 ", ""));
        });

        searchListView.setOnMouseClicked(e -> {
            User u = searchListView.getSelectionModel().getSelectedItem();
            if (u != null) {
                FirebaseService.getInstance().sendFriendRequest(u.getUid());
                showAlert("Friend request sent to " + u.getName());
            }
        });
    }

    // ─────────────────────────────────────────────
    // AVATAR HELPERS
    // ─────────────────────────────────────────────

    /** Decode a Base64 data URI and return a JavaFX Image, or null on failure. */
    private Image decodeBase64Image(String dataUri) {
        try {
            // dataUri format: "data:image/jpeg;base64,<b64data>"
            String b64 = dataUri.contains(",") ? dataUri.split(",", 2)[1] : dataUri;
            byte[] bytes = Base64.getDecoder().decode(b64);
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private void loadMyAvatar(String dataUri) {
        if (dataUri == null) return;
        Image img = decodeBase64Image(dataUri);
        if (img != null) {
            currentUserAvatar.setImage(img);
            currentUserAvatar.setClip(new Circle(18, 18, 18));
        }
    }

    // ─────────────────────────────────────────────
    // PROFILE PICTURE CHANGE
    // ─────────────────────────────────────────────

    @FXML
    private void handleChangeProfilePicture() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Profile Picture (max 500 KB)");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = fc.showOpenDialog(sendBtn.getScene().getWindow());
        if (file == null) return;

        FirebaseService.getInstance().uploadProfilePicture(file)
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
    // FRIENDS
    // ─────────────────────────────────────────────

    private void loadFriends() {
        friendsListView.getItems().clear();
        friendsList.clear();
        FirebaseService.getInstance().getAllFriends((uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                friendsList.add(u);
                friendsListView.getItems().add(u);
            })
        );
        friendsListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setGraphic(empty || u == null ? null : buildUserCell(u, false));
            }
        });
    }

    // ─────────────────────────────────────────────
    // GROUPS
    // ─────────────────────────────────────────────

    private void loadGroups() {
        groupsListView.getItems().clear();
        groupIdList.clear();
        FirebaseService.getInstance().getUserGroups((groupId, groupName) ->
            Platform.runLater(() -> {
                groupsListView.getItems().add("👥 " + groupName);
                groupIdList.add(groupId);
            })
        );
    }

    @FXML
    private void handleCreateGroup() {
        // Step 1: group name
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Create Group");
        d.setHeaderText("Enter a group name:");
        d.setContentText("Name:");
        String name = d.showAndWait().orElse(null);
        if (name == null || name.trim().isEmpty()) return;

        // Step 2: pick members
        Stage pick = new Stage();
        pick.initModality(Modality.APPLICATION_MODAL);
        pick.setTitle("Add Members");

        ListView<User> pickList = new ListView<>();
        pickList.getItems().addAll(friendsList);
        pickList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        pickList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getName());
            }
        });

        Button ok = new Button("Create");
        ok.setStyle("-fx-background-color:#7c6af7;-fx-text-fill:white;" +
                    "-fx-background-radius:8;-fx-padding:8 20;");
        List<String> selected = new ArrayList<>();
        ok.setOnAction(e -> {
            pickList.getSelectionModel().getSelectedItems()
                    .forEach(u -> selected.add(u.getUid()));
            pick.close();
        });

        VBox layout = new VBox(10,
            new Label("Select friends to add:"), pickList, ok);
        layout.setPadding(new Insets(16));
        pick.setScene(new Scene(layout, 300, 400));
        pick.showAndWait();

        if (selected.isEmpty()) { showAlert("Select at least one member."); return; }

        FirebaseService.getInstance().createGroup(name.trim(), selected)
            .thenAccept(groupId -> Platform.runLater(() -> {
                groupsListView.getItems().add("👥 " + name.trim());
                groupIdList.add(groupId);
                showAlert("Group \"" + name.trim() + "\" created!");
            }));
    }

    private void openGroupChat(String groupId, String groupName) {
        activeFriend    = null;
        activeGroupId   = groupId;
        activeGroupName = groupName;
        messagesBox.getChildren().clear();

        chatHeaderName.setText("👥 " + groupName);
        chatHeaderStatus.setText("Group Chat");
        chatHeaderAvatar.setImage(null);
        welcomePane.setVisible(false);
        chatPane.setVisible(true);

        FirebaseService.getInstance().listenForGroupMessages(groupId,
            (from, sender, text, time, type, file) ->
                Platform.runLater(() -> {
                    boolean mine = from.equals(FirebaseService.currentUserUID);
                    addBubble(text, mine, formatTime(time), type, file,
                              mine ? null : sender);
                    scrollToBottom();
                })
        );
    }

    // ─────────────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────────────

    @FXML
    private void handleSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        searchListView.getItems().clear();

        FirebaseService.getInstance().searchUsers(q, (uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                searchListView.getItems().add(u);
            })
        );
        searchListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setGraphic(empty || u == null ? null : buildUserCell(u, true));
            }
        });
    }

    // ─────────────────────────────────────────────
    // FRIEND REQUESTS
    // ─────────────────────────────────────────────

    private void loadIncomingRequests() {
        requestsListView.getItems().clear();
        FirebaseService.getInstance().getIncomingRequests((uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                requestsListView.getItems().add(u);
            })
        );
        requestsListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                HBox cell = buildUserCell(u, false);
                Button accept  = new Button("✓");
                Button decline = new Button("✗");
                accept.getStyleClass().add("accept-btn");
                decline.getStyleClass().add("decline-btn");
                accept.setOnAction(e -> {
                    FirebaseService.getInstance().acceptFriendRequest(u.getUid());
                    requestsListView.getItems().remove(u);
                    loadFriends();
                });
                decline.setOnAction(e -> {
                    FirebaseService.getInstance().declineFriendRequest(u.getUid());
                    requestsListView.getItems().remove(u);
                });
                cell.getChildren().addAll(accept, decline);
                setGraphic(cell);
            }
        });
    }

    // ─────────────────────────────────────────────
    // OPEN 1-ON-1 CHAT
    // ─────────────────────────────────────────────

    private void openChat(User user) {
        activeFriend  = user;
        activeGroupId = null;
        messagesBox.getChildren().clear();

        chatHeaderName.setText(user.getName());
        chatHeaderStatus.setText("• offline");
        welcomePane.setVisible(false);
        chatPane.setVisible(true);

        // Show friend avatar
        String pic = user.getProfileImage();
        if (pic != null && !pic.equals("default") && pic.startsWith("data:")) {
            Image img = decodeBase64Image(pic);
            if (img != null) {
                chatHeaderAvatar.setImage(img);
                chatHeaderAvatar.setClip(new Circle(19, 19, 19));
            }
        } else {
            chatHeaderAvatar.setImage(null);
        }

        // Live status
        FirebaseService.getInstance().listenForStatusChange(user.getUid(),
            status -> Platform.runLater(() ->
                chatHeaderStatus.setText("• " + status)));

        // Load messages
        FirebaseService.getInstance().listenForMessages(user.getUid(),
            (from, text, time, type, file) ->
                Platform.runLater(() -> {
                    boolean mine = from.equals(FirebaseService.currentUserUID);
                    addBubble(text, mine, formatTime(time), type, file, null);
                    scrollToBottom();
                })
        );

        FirebaseService.getInstance().markMessagesAsSeen(user.getUid());
    }

    // ─────────────────────────────────────────────
    // SEND — TEXT
    // ─────────────────────────────────────────────

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        if (activeFriend != null)
            FirebaseService.getInstance().sendMessage(activeFriend.getUid(), text);
        else if (activeGroupId != null)
            FirebaseService.getInstance().sendGroupMessage(activeGroupId, text, "text", null);

        messageInput.clear();
    }

    // ─────────────────────────────────────────────
    // SEND — FILE / IMAGE / AUDIO
    // ─────────────────────────────────────────────

    @FXML
    private void handleSendFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Send File (max 500 KB)");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images",
                "*.jpg","*.jpeg","*.png","*.gif","*.webp"),
            new FileChooser.ExtensionFilter("Documents",
                "*.pdf","*.docx","*.txt","*.xlsx","*.zip"),
            new FileChooser.ExtensionFilter("Audio",
                "*.mp3","*.wav","*.ogg","*.m4a"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fc.showOpenDialog(sendBtn.getScene().getWindow());
        if (file == null) return;

        String type = detectType(file.getName());

        if (activeFriend != null) {
            FirebaseService.getInstance()
                .sendFileMessage(activeFriend.getUid(), file, type)
                .exceptionally(e -> {
                    Platform.runLater(() -> showAlert(e.getMessage())); return null; });
        } else if (activeGroupId != null) {
            FirebaseService.getInstance()
                .sendGroupFileMessage(activeGroupId, file, type)
                .exceptionally(e -> {
                    Platform.runLater(() -> showAlert(e.getMessage())); return null; });
        }
    }

    // ─────────────────────────────────────────────
    // RECORD AUDIO
    // ─────────────────────────────────────────────

    @FXML
    private void handleRecordAudio() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Voice Message");

        Label status  = new Label("Press Record to start");
        status.setStyle("-fx-text-fill:#a8a8c0;-fx-font-size:13px;");
        Button recBtn  = new Button("🎙  Record");
        Button stopBtn = new Button("⏹  Stop & Send");
        stopBtn.setDisable(true);

        recBtn.setStyle("-fx-background-color:#7c6af7;-fx-text-fill:white;" +
                        "-fx-background-radius:8;-fx-padding:10 22;-fx-font-size:13px;");
        stopBtn.setStyle("-fx-background-color:#ef4444;-fx-text-fill:white;" +
                         "-fx-background-radius:8;-fx-padding:10 22;-fx-font-size:13px;");

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
                if (activeFriend != null)
                    FirebaseService.getInstance()
                        .sendFileMessage(activeFriend.getUid(), outFile[0], "audio");
                else if (activeGroupId != null)
                    FirebaseService.getInstance()
                        .sendGroupFileMessage(activeGroupId, outFile[0], "audio");
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
    // EMOJI PICKER
    // ─────────────────────────────────────────────

    @FXML
    private void handleEmojiPicker() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Emojis");

        TilePane tile = new TilePane();
        tile.setHgap(4); tile.setVgap(4);
        tile.setPadding(new Insets(10));
        tile.setPrefColumns(6);
        tile.setStyle("-fx-background-color:#1a1a24;");

        for (String emoji : EMOJIS) {
            Button b = new Button(emoji);
            b.setStyle("-fx-font-size:20px;-fx-background-color:transparent;" +
                       "-fx-cursor:hand;-fx-padding:6;");
            b.setOnAction(e -> {
                messageInput.appendText(emoji);
                stage.close();
            });
            b.setOnMouseEntered(e ->
                b.setStyle("-fx-font-size:20px;-fx-background-color:#2a2a3e;" +
                           "-fx-background-radius:6;-fx-cursor:hand;-fx-padding:6;"));
            b.setOnMouseExited(e ->
                b.setStyle("-fx-font-size:20px;-fx-background-color:transparent;" +
                           "-fx-cursor:hand;-fx-padding:6;"));
            tile.getChildren().add(b);
        }

        ScrollPane sp = new ScrollPane(tile);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#1a1a24;-fx-border-color:transparent;");
        stage.setScene(new Scene(sp, 240, 200));
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────
    // MESSAGE BUBBLE RENDERER
    // ─────────────────────────────────────────────

    private void addBubble(String content, boolean mine, String time,
                            String type, String fileName, String senderName) {
        HBox row = new HBox();
        row.setPadding(new Insets(3, 12, 3, 12));
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox msgBox = new VBox(3);
        msgBox.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Group sender name
        if (senderName != null) {
            Label nl = new Label(senderName);
            nl.setStyle("-fx-text-fill:#a898ff;-fx-font-size:10px;-fx-padding:0 4 1 4;");
            msgBox.getChildren().add(nl);
        }

        if ("image".equals(type)) {
            // Decode Base64 image
            Image img = decodeBase64Image(content);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(220); iv.setPreserveRatio(true);
                VBox imgBubble = new VBox(iv);
                imgBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
                imgBubble.setPadding(new Insets(6));
                msgBox.getChildren().add(imgBubble);
            } else {
                msgBox.getChildren().add(textBubble("[Image]", mine));
            }

        } else if ("audio".equals(type)) {
            // Audio — decode and play from temp file
            Button playBtn = new Button("▶  Play Voice");
            playBtn.getStyleClass().add(mine ? "audio-btn-mine" : "audio-btn-theirs");
            Label fileLabel = new Label(fileName != null ? fileName : "Voice Message");
            fileLabel.setStyle("-fx-text-fill:#888;-fx-font-size:11px;");
            HBox audioRow = new HBox(10, playBtn, fileLabel);
            audioRow.setAlignment(Pos.CENTER_LEFT);

            playBtn.setOnAction(e -> playAudioFromBase64(content, playBtn));

            VBox audioBubble = new VBox(audioRow);
            audioBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
            audioBubble.setPadding(new Insets(10, 14, 10, 14));
            msgBox.getChildren().add(audioBubble);

        } else if ("file".equals(type)) {
            String display = fileName != null ? fileName : "File";
            Label fileLbl = new Label("📎 " + display);
            fileLbl.setWrapText(true);
            fileLbl.setMaxWidth(260);
            Label hint = new Label("(file received — open in app to view)");
            hint.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
            VBox fileBubble = new VBox(4, fileLbl, hint);
            fileBubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
            fileBubble.setPadding(new Insets(8, 14, 8, 14));
            msgBox.getChildren().add(fileBubble);

        } else {
            // Plain text
            msgBox.getChildren().add(textBubble(content, mine));
        }

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("time-label");
        msgBox.getChildren().add(timeLabel);
        row.getChildren().add(msgBox);
        messagesBox.getChildren().add(row);
    }

    private Label textBubble(String text, boolean mine) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(340);
        l.setPadding(new Insets(10, 14, 10, 14));
        l.getStyleClass().add(mine ? "bubble-mine" : "bubble-theirs");
        return l;
    }

    /** Decode Base64 audio data URI, write to temp file, play with MediaPlayer. */
    private void playAudioFromBase64(String dataUri, Button playBtn) {
    new Thread(() -> {
        try {
            String b64   = dataUri.contains(",") ? dataUri.split(",", 2)[1] : dataUri;
            byte[] bytes = Base64.getDecoder().decode(b64);
            File tmp     = File.createTempFile("play_", ".wav");
            tmp.deleteOnExit();
            java.nio.file.Files.write(tmp.toPath(), bytes);

            // Use javax.sound.sampled instead of javafx.media
            javax.sound.sampled.AudioInputStream ais =
                javax.sound.sampled.AudioSystem.getAudioInputStream(tmp);
            javax.sound.sampled.Clip clip =
                javax.sound.sampled.AudioSystem.getClip();
            clip.open(ais);

            Platform.runLater(() -> playBtn.setText("▶  Playing..."));
            clip.start();

            // Wait for it to finish, then reset button
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
    // CELL BUILDER
    // ─────────────────────────────────────────────

    private HBox buildUserCell(User user, boolean addHint) {
        StackPane avatarPane = new StackPane();
        avatarPane.setMinSize(38, 38);
        avatarPane.setMaxSize(38, 38);

        String pic = user.getProfileImage();
        if (pic != null && !pic.equals("default") && pic.startsWith("data:")) {
            Image img = decodeBase64Image(pic);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(38); iv.setFitHeight(38);
                iv.setPreserveRatio(true);
                iv.setClip(new Circle(19, 19, 19));
                avatarPane.getChildren().add(iv);
            } else {
                avatarPane.getChildren().add(initialAvatar(user.getName()));
            }
        } else {
            avatarPane.getChildren().add(initialAvatar(user.getName()));
        }

        Label nameLbl = new Label(user.getName());
        nameLbl.getStyleClass().add("cell-name");
        Label subLbl  = new Label(addHint ? "Tap to send friend request" : user.getEmail());
        subLbl.getStyleClass().add("cell-sub");

        VBox info = new VBox(2, nameLbl, subLbl);
        HBox cell = new HBox(12, avatarPane, info);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(6, 8, 6, 8));
        return cell;
    }

    private Label initialAvatar(String name) {
        String ch = (name != null && !name.isEmpty())
            ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        Label l = new Label(ch);
        l.getStyleClass().add("avatar");
        l.setMinSize(38, 38); l.setMaxSize(38, 38);
        return l;
    }

    // ─────────────────────────────────────────────
    // LOGOUT
    // ─────────────────────────────────────────────

    @FXML
    private void handleLogout() {
        FirebaseService.getInstance().logout();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/WeConnect/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            Scene scene = new Scene(root, 420, 580);
            scene.getStylesheets().add(
                getClass().getResource("/com/WeConnect/css/style.css").toExternalForm());
            com.WeConnect.Main.primaryStage.setScene(scene);
            com.WeConnect.Main.primaryStage.setTitle("WeConnect — Login");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─────────────────────────────────────────────
    // UTILS
    // ─────────────────────────────────────────────

    private String formatTime(long ts) {
        return new SimpleDateFormat("hh:mm a").format(new Date(ts));
    }

    private String detectType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg")||n.endsWith(".jpeg")||
            n.endsWith(".png")||n.endsWith(".gif")||n.endsWith(".webp")) return "image";
        if (n.endsWith(".mp3")||n.endsWith(".wav")||
            n.endsWith(".ogg")||n.endsWith(".m4a"))                       return "audio";
        return "file";
    }

    private void scrollToBottom() {
        chatScrollPane.setVvalue(1.0);
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
