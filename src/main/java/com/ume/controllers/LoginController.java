package com.ume.controllers;

import com.ume.Main;
import com.ume.services.FirebaseService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginBtn;
    @FXML private Label errorLabel;
    @FXML private Label switchLabel;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        loginBtn.setDisable(true);
        loginBtn.setText("Logging in...");

        FirebaseService.getInstance().loginUser(email, password)
                .thenAccept(uid -> Platform.runLater(() -> {
                    try {
                        navigateToDashboard();
                    } catch (Exception e) {
                        showError("Navigation failed: " + e.getMessage());
                        e.printStackTrace();
                        System.err.println("Cause: " + e.getCause());
                    }
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        showError("Login failed. Check credentials.");
                        loginBtn.setDisable(false);
                        loginBtn.setText("Login");
                    });
                    return null;
                });
    }

    @FXML
    private void goToRegister() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/ume/fxml/register.fxml"));
            Scene scene = new Scene(root, 420, 580);
            scene.getStylesheets().add(getClass().getResource("/com/ume/css/style.css").toExternalForm());
            Main.primaryStage.setTitle("uMe — Register");
            Main.primaryStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateToDashboard() throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/ume/fxml/dashboard.fxml"));
        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/com/ume/css/style.css").toExternalForm());
        Main.primaryStage.setTitle("uMe — " + FirebaseService.currentUserName);
        Main.primaryStage.setResizable(true);
        Main.primaryStage.setScene(scene);
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
