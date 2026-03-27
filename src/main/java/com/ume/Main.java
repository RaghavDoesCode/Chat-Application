package com.ume;

import com.ume.services.FirebaseService;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    public static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        FirebaseService.initialize();
        primaryStage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ume/fxml/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 420, 580);
        scene.getStylesheets().add(getClass().getResource("/com/ume/css/style.css").toExternalForm());
        stage.setTitle("WeConnect — Login");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
