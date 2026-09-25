package application;

import database.Database;
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
            Database.initializeDatabase();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/MainWindow.fxml"));
            Parent root = loader.load();
            MainWindowController controller = loader.getController();

            Scene scene = new Scene(root, 1400, 850);
            scene.getStylesheets().add(
                    getClass().getResource("/application/style.css").toExternalForm()
            );

            primaryStage.setTitle("Personalized Activity and Screen Time Manager");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);

            primaryStage.setOnCloseRequest(e -> {
                System.out.println("[Main] window closing - graceful shutdown");
                controller.stopAll();
                ThreadManager.getInstance().shutdown();
            });

            primaryStage.show();
            System.out.println("[Main] application started successfully");

        } catch (Exception e) {
            System.err.println("[Main] failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ThreadManager.getInstance().shutdown();
        }, "shutdown-hook"));
        launch(args);
    }
}