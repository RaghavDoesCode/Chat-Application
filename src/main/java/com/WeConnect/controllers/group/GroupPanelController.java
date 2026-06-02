package com.WeConnect.controllers.group;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.WeConnect.controllers.chat.ChatContext;
import com.WeConnect.controllers.chat.MessageRenderer;
import com.WeConnect.models.User;
import com.WeConnect.services.AuthService;
import com.WeConnect.services.GroupService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * GroupPanelController — handles group chat UI logic.
 *
 * Responsibilities:
 *   - Open a group chat (set header, attach listener, render messages)
 *   - Create a new group (two-step dialog: name → pick members)
 *
 * Needs ChatContext to update the active group and push bubbles to messagesBox.
 * Needs MessageRenderer to render incoming group messages as bubbles.
 */
public class GroupPanelController {

    private final ChatContext     ctx;
    private final MessageRenderer renderer;
    private final List<User>      friendsList;

    // Header UI references injected by DashboardController
    private final Label     chatHeaderName;
    private final Label     chatHeaderStatus;
    private final ImageView chatHeaderAvatar;
    private final VBox      chatPane;
    private final VBox      welcomePane;
    private final ListView<String> groupsListView;
    private final List<String>     groupIdList;

    public GroupPanelController(ChatContext ctx,
                                 MessageRenderer renderer,
                                 List<User> friendsList,
                                 Label chatHeaderName,
                                 Label chatHeaderStatus,
                                 ImageView chatHeaderAvatar,
                                 VBox chatPane,
                                 VBox welcomePane,
                                 ListView<String> groupsListView,
                                 List<String> groupIdList) {
        this.ctx             = ctx;
        this.renderer        = renderer;
        this.friendsList     = friendsList;
        this.chatHeaderName  = chatHeaderName;
        this.chatHeaderStatus= chatHeaderStatus;
        this.chatHeaderAvatar= chatHeaderAvatar;
        this.chatPane        = chatPane;
        this.welcomePane     = welcomePane;
        this.groupsListView  = groupsListView;
        this.groupIdList     = groupIdList;
    }

    // ─────────────────────────────────────────────
    // OPEN GROUP CHAT
    // ─────────────────────────────────────────────

    /**
     * Opens a group chat session.
     * Clears previous messages, updates the header, and attaches
     * a real-time listener for incoming group messages.
     */
    public void openGroupChat(String groupId, String groupName) {
        ctx.reset();
        ctx.activeGroupId   = groupId;
        ctx.activeGroupName = groupName;
        ctx.messagesBox.getChildren().clear();

        chatHeaderName.setText("👥 " + groupName);
        chatHeaderStatus.setText("Group Chat");
        chatHeaderAvatar.setImage(null);
        welcomePane.setVisible(false);
        chatPane.setVisible(true);

        GroupService.listenForGroupMessages(groupId,
            (from, senderName, text, time, type, file) ->
                Platform.runLater(() -> {
                    boolean mine = from.equals(AuthService.currentUserUID);
                    renderer.addBubble(
                        "grp_" + time, text, mine,
                        formatTime(time), type, file,
                        mine ? null : senderName, null);
                    scrollToBottom();
                })
        );
    }

    // ─────────────────────────────────────────────
    // CREATE GROUP
    // ─────────────────────────────────────────────

    /**
     * Two-step dialog to create a group:
     *   Step 1 — TextInputDialog for group name
     *   Step 2 — ListView with multi-select to pick members from friends list
     *
     * On success, adds the new group to groupsListView and groupIdList immediately
     * so the user doesn't need to refresh.
     */
    public void handleCreateGroup() {
        // Step 1: get group name
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
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getName());
            }
        });

        Button ok = new Button("Create");
        ok.setStyle("-fx-background-color:#7c6af7;-fx-text-fill:white;"
            + "-fx-background-radius:8;-fx-padding:8 20;");

        List<String> selected = new ArrayList<>();
        ok.setOnAction(e -> {
            pickList.getSelectionModel().getSelectedItems()
                .forEach(u -> selected.add(u.getUid()));
            pick.close();
        });

        VBox layout = new VBox(10, new Label("Select friends to add:"), pickList, ok);
        layout.setPadding(new Insets(16));
        pick.setScene(new Scene(layout, 300, 400));
        pick.showAndWait();

        if (selected.isEmpty()) {
            showAlert("Select at least one member.");
            return;
        }

        GroupService.createGroup(name.trim(), selected)
            .thenAccept(groupId -> Platform.runLater(() -> {
                groupsListView.getItems().add("👥 " + name.trim());
                groupIdList.add(groupId);
                showAlert("Group \"" + name.trim() + "\" created!");
            }));
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private void scrollToBottom() {
        ctx.chatScrollPane.setVvalue(1.0);
    }

    private String formatTime(long ts) {
        return new SimpleDateFormat("hh:mm a").format(new Date(ts));
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}