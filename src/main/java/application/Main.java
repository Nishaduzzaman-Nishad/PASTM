package application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/application/MainWindow.fxml"));
            Scene scene = new Scene(root, 1400, 850);

            // Load the CSS (Week 3)
            scene.getStylesheets().add(getClass().getResource("/application/style.css").toExternalForm());

            primaryStage.setTitle("Personalized Activity and Screen Time Manager");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}