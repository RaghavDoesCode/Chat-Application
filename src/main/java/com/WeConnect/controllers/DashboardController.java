package com.WeConnect.controllers;

import java.util.ArrayList;
import java.util.List;

import com.WeConnect.controllers.chat.ChatContext;
import com.WeConnect.controllers.chat.ChatPanelController;
import com.WeConnect.controllers.chat.MediaHandler;
import com.WeConnect.controllers.chat.MessageRenderer;
import com.WeConnect.controllers.group.GroupPanelController;
import com.WeConnect.controllers.profile.ProfileController;
import com.WeConnect.models.User;
import com.WeConnect.services.AuthService;
import com.WeConnect.services.FriendService;
import com.WeConnect.services.GroupService;

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
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * DashboardController — the top-level coordinator for the dashboard screen.
 *
 * This class now has exactly three jobs:
 *   1. Receive all @FXML-injected UI nodes (wiring)
 *   2. Build and connect the sub-controllers in initialize()
 *   3. Forward @FXML action events to the right sub-controller
 *
 * All actual logic lives in:
 *   ChatPanelController  — 1-on-1 chat open/send
 *   MessageRenderer      — bubble rendering and tick updates
 *   MediaHandler         — file send, audio record/play, save
 *   GroupPanelController — group chat open and create
 *   ProfileController    — avatar display and upload
 */
public class DashboardController {

    // ── Left panel ───────────────────────────────────────────────────────────
    @FXML private TabPane          leftTabPane;
    @FXML private ListView<User>   friendsListView;
    @FXML private ListView<User>   requestsListView;
    @FXML private ListView<User>   searchListView;
    @FXML private ListView<String> groupsListView;
    @FXML private TextField        searchField;
    @FXML private Label            currentUserNameLabel;
    @FXML private ImageView        currentUserAvatar;
    @FXML private StackPane        avatarPane;
    @FXML private Label            defaultAvatarLabel;

    // ── Right panel ──────────────────────────────────────────────────────────
    @FXML private VBox       chatPane;
    @FXML private Label      chatHeaderName;
    @FXML private Label      chatHeaderStatus;
    @FXML private ImageView  chatHeaderAvatar;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox       messagesBox;
    @FXML private TextField  messageInput;
    @FXML private Button     sendBtn;
    @FXML private VBox       welcomePane;

    // ── Sub-controllers (built in initialize) ────────────────────────────────
    private ChatContext          chatContext;
    private ChatPanelController  chatPanel;
    private GroupPanelController groupPanel;
    private ProfileController    profileCtrl;
    private MediaHandler         mediaHandler;

    // ── Local list state ─────────────────────────────────────────────────────
    private final List<User>   friendsList = new ArrayList<>();
    private final List<String> groupIdList = new ArrayList<>();

    private static final String[] EMOJIS = {
        "😀","😂","😍","😎","😢","😡","👍","👎","❤️","🔥",
        "🎉","😊","🤔","😴","🥳","👏","🙌","💯","✅","🚀",
        "😇","🤩","🥺","😤","🤯","🙏","💪","✌️","🫡","💬"
    };

    // ─────────────────────────────────────────────
    // INITIALIZE — called automatically after FXML loads
    // ─────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Show welcome pane, hide chat pane until a chat is opened
        welcomePane.setVisible(true);
        chatPane.setVisible(false);

        currentUserNameLabel.setText(AuthService.currentUserName);

        // Build shared state holder
        chatContext = new ChatContext();
        chatContext.messagesBox    = messagesBox;
        chatContext.chatScrollPane = chatScrollPane;
        chatContext.messageInput   = messageInput;

        // Build sub-controllers — order matters: renderer before chatPanel
        mediaHandler = new MediaHandler(chatContext, sendBtn.getScene() != null
            ? sendBtn.getScene().getWindow() : null);

        MessageRenderer renderer = new MessageRenderer(chatContext, mediaHandler);

        chatPanel = new ChatPanelController(
            chatContext, renderer,
            chatHeaderName, chatHeaderStatus, chatHeaderAvatar,
            chatPane, welcomePane);

        groupPanel = new GroupPanelController(
            chatContext, renderer, friendsList,
            chatHeaderName, chatHeaderStatus, chatHeaderAvatar,
            chatPane, welcomePane, groupsListView, groupIdList);

        profileCtrl = new ProfileController(
            currentUserAvatar, defaultAvatarLabel,
            avatarPane.getScene() != null ? avatarPane.getScene().getWindow() : null);

        // Load avatar, friends, requests, groups
        profileCtrl.loadMyAvatar(AuthService.currentUserProfileBase64);
        loadFriends();
        loadIncomingRequests();
        loadGroups();

        // Click handlers
        friendsListView.setOnMouseClicked(e -> {
            User u = friendsListView.getSelectionModel().getSelectedItem();
            if (u != null) chatPanel.openChat(u);
        });

        groupsListView.setOnMouseClicked(e -> {
            int i = groupsListView.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < groupIdList.size())
                groupPanel.openGroupChat(groupIdList.get(i),
                    groupsListView.getItems().get(i).replace("👥 ", ""));
        });

        searchListView.setOnMouseClicked(e -> {
            User u = searchListView.getSelectionModel().getSelectedItem();
            if (u != null) {
                FriendService.sendFriendRequest(u.getUid());
                showAlert("Friend request sent to " + u.getName());
            }
        });

        // MediaHandler needs the window — available after scene is shown.
        // Re-set it here via a scene listener so it's never null.
        sendBtn.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obs2, oldWin, newWin) -> {
                    if (newWin != null) {
                        mediaHandler = new MediaHandler(chatContext, newWin);
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────
    // LOAD LISTS
    // ─────────────────────────────────────────────

    private void loadFriends() {
        friendsListView.getItems().clear();
        friendsList.clear();
        FriendService.getAllFriends((uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                friendsList.add(u);
                friendsListView.getItems().add(u);
            })
        );
        friendsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setGraphic(empty || u == null ? null : buildUserCell(u, false));
            }
        });
    }

    private void loadGroups() {
        groupsListView.getItems().clear();
        groupIdList.clear();
        GroupService.getUserGroups((groupId, groupName) ->
            Platform.runLater(() -> {
                groupsListView.getItems().add("👥 " + groupName);
                groupIdList.add(groupId);
            })
        );
    }

    private void loadIncomingRequests() {
        requestsListView.getItems().clear();
        FriendService.getIncomingRequests((uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                requestsListView.getItems().add(u);
            })
        );
        requestsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                HBox cell    = buildUserCell(u, false);
                Button accept  = new Button("✓");
                Button decline = new Button("✗");
                accept.getStyleClass().add("accept-btn");
                decline.getStyleClass().add("decline-btn");
                accept.setOnAction(e -> {
                    FriendService.acceptFriendRequest(u.getUid());
                    requestsListView.getItems().remove(u);
                    loadFriends();
                });
                decline.setOnAction(e -> {
                    FriendService.declineFriendRequest(u.getUid());
                    requestsListView.getItems().remove(u);
                });
                cell.getChildren().addAll(accept, decline);
                setGraphic(cell);
            }
        });
    }

    // ─────────────────────────────────────────────
    // @FXML ACTION HANDLERS — pure delegation
    // ─────────────────────────────────────────────

    @FXML private void handleAvatarClick()   { profileCtrl.handleAvatarClick(); }
    @FXML private void handleSendMessage()   { chatPanel.handleSendMessage(); }
    @FXML private void handleSendFile()      { mediaHandler.handleSendFile(); }
    @FXML private void handleRecordAudio()   { mediaHandler.handleRecordAudio(); }
    @FXML private void handleCreateGroup()   { groupPanel.handleCreateGroup(); }

    @FXML
    private void handleSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        searchListView.getItems().clear();
        FriendService.searchUsers(q, (uid, name, email, pic) ->
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                u.setProfileImage(pic);
                searchListView.getItems().add(u);
            })
        );
        searchListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setGraphic(empty || u == null ? null : buildUserCell(u, true));
            }
        });
    }

    @FXML
    private void handleEmojiPicker() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Emojis");

        TilePane tile = new TilePane();
        tile.setHgap(4);
        tile.setVgap(4);
        tile.setPadding(new Insets(10));
        tile.setPrefColumns(6);
        tile.setStyle("-fx-background-color:#1a1a24;");

        for (String emoji : EMOJIS) {
            Button b = new Button(emoji);
            b.setStyle("-fx-font-size:20px;-fx-background-color:transparent;"
                + "-fx-cursor:hand;-fx-padding:6;");
            b.setOnAction(e -> { messageInput.appendText(emoji); stage.close(); });
            b.setOnMouseEntered(e -> b.setStyle("-fx-font-size:20px;"
                + "-fx-background-color:#2a2a3e;-fx-background-radius:6;"
                + "-fx-cursor:hand;-fx-padding:6;"));
            b.setOnMouseExited(e -> b.setStyle("-fx-font-size:20px;"
                + "-fx-background-color:transparent;-fx-cursor:hand;-fx-padding:6;"));
            tile.getChildren().add(b);
        }

        ScrollPane sp = new ScrollPane(tile);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#1a1a24;-fx-border-color:transparent;");
        stage.setScene(new Scene(sp, 240, 200));
        stage.showAndWait();
    }

    @FXML
    private void handleLogout() {
        AuthService.logout();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/WeConnect/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            Scene scene = new Scene(root, 420, 580);
            scene.getStylesheets().add(
                getClass().getResource("/com/WeConnect/css/style.css").toExternalForm());
            com.WeConnect.Main.primaryStage.setScene(scene);
            com.WeConnect.Main.primaryStage.setTitle("WeConnect — Login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    // CELL BUILDER + UTILS  (UI helpers, stay here since they're view-only)
    // ─────────────────────────────────────────────

    private HBox buildUserCell(User user, boolean addHint) {
        StackPane ap = new StackPane();
        ap.setMinSize(38, 38);
        ap.setMaxSize(38, 38);

        String pic = user.getProfileImage();
        if (pic != null && !pic.equals("default") && pic.startsWith("data:")) {
            Image img = MessageRenderer.decodeBase64Image(pic);
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(38);
                iv.setFitHeight(38);
                iv.setPreserveRatio(true);
                iv.setClip(new Circle(19, 19, 19));
                ap.getChildren().add(iv);
            } else {
                ap.getChildren().add(initialAvatar(user.getName()));
            }
        } else {
            ap.getChildren().add(initialAvatar(user.getName()));
        }

        Label nameLbl = new Label(user.getName());
        nameLbl.getStyleClass().add("cell-name");
        Label subLbl  = new Label(addHint ? "Tap to send friend request" : user.getEmail());
        subLbl.getStyleClass().add("cell-sub");

        VBox info = new VBox(2, nameLbl, subLbl);
        HBox cell = new HBox(12, ap, info);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(6, 8, 6, 8));
        return cell;
    }

    private Label initialAvatar(String name) {
        String ch = (name != null && !name.isEmpty())
            ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        Label l = new Label(ch);
        l.getStyleClass().add("avatar");
        l.setMinSize(38, 38);
        l.setMaxSize(38, 38);
        return l;
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}