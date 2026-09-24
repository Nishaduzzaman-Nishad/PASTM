package application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import service.ThreadManager;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/application/MainWindow.fxml"));
            Scene scene = new Scene(root, 1400, 850);
            scene.getStylesheets().add(getClass().getResource("/application/style.css").toExternalForm());

            primaryStage.setTitle("Personalized Activity and Screen Time Manager");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);

            // Week 4: graceful shutdown on window close
            primaryStage.setOnCloseRequest(e -> {
                System.out.println("[Main] window closing — running graceful shutdown");
                ThreadManager.getInstance().shutdown();
            });

            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Also protect against JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ThreadManager.getInstance().shutdown();
        }, "shutdown-hook"));

        launch(args);
    }
}