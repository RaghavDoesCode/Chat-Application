package com.ume.controllers;

import com.ume.Main;
import com.ume.services.FirebaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;

public class RegisterController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerBtn;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
    }

    @FXML
    private void handleRegister() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("All fields are required.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        registerBtn.setDisable(true);
        registerBtn.setText("Creating account...");

        FirebaseService.getInstance().registerUser(name, email, password)
                .thenAccept(uid -> Platform.runLater(() -> {
                    try {
                        goToLogin();
                    } catch (Exception e) {
                        showError("Error: " + e.getMessage());
                    }
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        showError("Registration failed: " + e.getMessage());
                        registerBtn.setDisable(false);
                        registerBtn.setText("Register");
                    });
                    return null;
                });
    }

    @FXML
    private void goToLogin() throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/ume/fxml/login.fxml"));
        Scene scene = new Scene(root, 420, 580);
        scene.getStylesheets().add(getClass().getResource("/com/ume/css/style.css").toExternalForm());
        Main.primaryStage.setTitle("uMe — Login");
        Main.primaryStage.setScene(scene);
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
