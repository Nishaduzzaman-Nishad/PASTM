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
            Scene scene = new Scene(root, 1000, 700);

            scene.getStylesheets().add(getClass().getResource("/application/style.css").toExternalForm());

            primaryStage.setTitle("Personalized Activity and Screen Time Manager");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}