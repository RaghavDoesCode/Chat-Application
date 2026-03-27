package com.WeConnect.controllers;

import java.util.ArrayList;
import java.util.List;

import com.WeConnect.models.Message;
import com.WeConnect.models.User;
import com.WeConnect.services.FirebaseService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DashboardController {

    // Left panel — tabs
    @FXML private TabPane leftTabPane;
    @FXML private ListView<User> friendsListView;
    @FXML private ListView<User> requestsListView;
    @FXML private ListView<User> searchListView;
    @FXML private TextField searchField;

    // Right panel — chat
    @FXML private VBox chatPane;
    @FXML private Label chatHeaderName;
    @FXML private Label chatHeaderStatus;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesBox;
    @FXML private TextField messageInput;
    @FXML private Button sendBtn;
    @FXML private VBox welcomeLabel;

    // Current chat target
    private User activeChatUser = null;
    private final List<User> friendsList = new ArrayList<>();

    @FXML
    public void initialize() {
        chatPane.setVisible(false);
        welcomeLabel.setVisible(true);

        loadFriends();
        loadIncomingRequests();
        setupFriendListClick();
        setupSearchListClick();
        setupRequestListClick();
    }

    // ─────────────────────────────────────
    // FRIENDS
    // ─────────────────────────────────────

    private void loadFriends() {
        friendsListView.getItems().clear();
        FirebaseService.getInstance().getAllFriends((uid, name, email) -> {
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                friendsList.add(u);
                friendsListView.getItems().add(u);
            });
        });

        // Custom cell factory — shows name + avatar initial
        friendsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    setGraphic(buildUserCell(user, false));
                }
            }
        });
    }

    private void setupFriendListClick() {
        friendsListView.setOnMouseClicked(e -> {
            User selected = friendsListView.getSelectionModel().getSelectedItem();
            if (selected != null) openChat(selected);
        });
    }

    // ─────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        searchListView.getItems().clear();

        // Linear Search O(n) — highlight this in DAA presentation
        FirebaseService.getInstance().searchUsers(query, (uid, name, email) -> {
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                searchListView.getItems().add(u);
            });
        });

        searchListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    setGraphic(buildUserCell(user, true));
                }
            }
        });
    }

    private void setupSearchListClick() {
        searchListView.setOnMouseClicked(e -> {
            User selected = searchListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sendFriendRequest(selected);
            }
        });
    }

    private void sendFriendRequest(User user) {
        FirebaseService.getInstance().sendFriendRequest(user.getUid());
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Friend request sent to " + user.getName(), ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ─────────────────────────────────────
    // FRIEND REQUESTS (Graph edges)
    // ─────────────────────────────────────

    private void loadIncomingRequests() {
        requestsListView.getItems().clear();
        FirebaseService.getInstance().getIncomingRequests((uid, name, email) -> {
            Platform.runLater(() -> {
                User u = new User(uid, name, email);
                requestsListView.getItems().add(u);
            });
        });

        requestsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    HBox cell = buildUserCell(user, false);
                    Button acceptBtn = new Button("✓");
                    Button declineBtn = new Button("✗");
                    acceptBtn.getStyleClass().add("accept-btn");
                    declineBtn.getStyleClass().add("decline-btn");

                    acceptBtn.setOnAction(ev -> {
                        FirebaseService.getInstance().acceptFriendRequest(user.getUid());
                        requestsListView.getItems().remove(user);
                        loadFriends(); // refresh friends list
                    });
                    declineBtn.setOnAction(ev -> {
                        FirebaseService.getInstance().declineFriendRequest(user.getUid());
                        requestsListView.getItems().remove(user);
                    });

                    cell.getChildren().addAll(acceptBtn, declineBtn);
                    setGraphic(cell);
                }
            }
        });
    }

    private void setupRequestListClick() {
        // Handled by button clicks inside cells
    }

    // ─────────────────────────────────────
    // CHAT
    // ─────────────────────────────────────

    private void openChat(User user) {
        activeChatUser = user;
        messagesBox.getChildren().clear();

        chatHeaderName.setText(user.getName());
        chatHeaderStatus.setText("• " + user.getStatus());
        welcomeLabel.setVisible(false);
        chatPane.setVisible(true);

        // Attach real-time listener — Firebase pushes new messages as they arrive
        FirebaseService.getInstance().listenForMessages(user.getUid(), (fromUID, text, time) -> {
            Platform.runLater(() -> {
                boolean isMine = fromUID.equals(FirebaseService.currentUserUID);
                addMessageBubble(text, isMine, new Message(fromUID, text, time, true).getFormattedTime());
                scrollToBottom();
            });
        });
    }

    @FXML
    private void handleSendMessage() {
        if (activeChatUser == null) return;
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        // Fan-out write: writes to both sender/receiver paths
        FirebaseService.getInstance().sendMessage(activeChatUser.getUid(), text);
        messageInput.clear();
    }

    private void addMessageBubble(String text, boolean isMine, String time) {
        HBox row = new HBox();
        row.setPadding(new Insets(4, 12, 4, 12));
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(340);
        bubble.setPadding(new Insets(10, 14, 10, 14));
        bubble.getStyleClass().add(isMine ? "bubble-mine" : "bubble-theirs");

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("time-label");

        VBox msgBox = new VBox(2, bubble, timeLabel);
        msgBox.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        row.getChildren().add(msgBox);
        messagesBox.getChildren().add(row);
    }

    private void scrollToBottom() {
        chatScrollPane.setVvalue(1.0);
    }

    // ─────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────

    private HBox buildUserCell(User user, boolean showAddHint) {
        // Avatar circle with initial
        String initial = user.getName() != null && !user.getName().isEmpty()
                ? String.valueOf(user.getName().charAt(0)).toUpperCase() : "?";
        Label avatar = new Label(initial);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(38, 38);
        avatar.setMaxSize(38, 38);

        VBox info = new VBox(2);
        Label nameLabel = new Label(user.getName());
        nameLabel.getStyleClass().add("cell-name");
        Label emailLabel = new Label(showAddHint ? "Tap to send request" : user.getEmail());
        emailLabel.getStyleClass().add("cell-sub");
        info.getChildren().addAll(nameLabel, emailLabel);

        HBox cell = new HBox(12, avatar, info);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(6, 8, 6, 8));
        return cell;
    }

    @FXML
    private void handleLogout() {
        FirebaseService.getInstance().logout();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/WeConnect/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 420, 580);
            scene.getStylesheets().add(getClass().getResource("/com/WeConnect/css/style.css").toExternalForm());
            com.WeConnect.Main.primaryStage.setTitle("WeConnect — Login");
            com.WeConnect.Main.primaryStage.setResizable(false);
            com.WeConnect.Main.primaryStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
