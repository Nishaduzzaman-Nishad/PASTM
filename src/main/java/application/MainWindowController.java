package application;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.App;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainWindowController {

    // --- Week 3: UI Elements ---
    @FXML private ComboBox<String> appComboBox;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private Label timerLabel;
    @FXML private ProgressBar screenProgress;
    @FXML private TableView<App> activityTable;
    @FXML private TableColumn<App, String> appNameColumn;
    @FXML private TableColumn<App, String> startTimeColumn;
    @FXML private TableColumn<App, String> endTimeColumn;
    @FXML private TableColumn<App, Integer> durationColumn;
    @FXML private TableColumn<App, String> categoryColumn;
    @FXML private PieChart activityPieChart;

    // --- Week 4: Concurrency State ---
    private ScheduledExecutorService scheduler;
    private int secondsElapsed = 0;
    private String currentApp = "";
    private final int DEMO_LIMIT_SECONDS = 60; // 1 minute for easy testing

    @FXML
    public void initialize() {
        // 1. Setup TableView Columns (Week 3)
        appNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("totalSecondsUsed"));

        // 2. Dummy Data for the UI (Week 3)
        ObservableList<App> dummyData = FXCollections.observableArrayList(
                new App("YouTube", 60),
                new App("Facebook", 30),
                new App("Instagram", 45),
                new App("DSA Practice", 120)
        );
        activityTable.setItems(dummyData);

        // 3. Setup ComboBoxes (Week 3)
        appComboBox.getItems().addAll("YouTube", "Facebook", "Instagram", "WhatsApp", "Chrome", "TikTok");
        appComboBox.setValue("YouTube");
        appComboBox.setOnAction(e -> switchApp());

        filterComboBox.getItems().addAll("All Apps", "YouTube", "Facebook", "Instagram", "Study", "Sleep");
        filterComboBox.setValue("All Apps");

        // 4. Setup PieChart (Week 3)
        activityPieChart.getData().addAll(
                new PieChart.Data("Screen Time", 25),
                new PieChart.Data("Study Time", 25),
                new PieChart.Data("Sleep Time", 30),
                new PieChart.Data("Free Time", 20)
        );
    }

    // --- Week 4: The Threading Logic ---
    private void switchApp() {
        String newApp = appComboBox.getValue();
        if (newApp == null || newApp.equals(currentApp)) return;

        // Synchronized block prevents Race Conditions (Week 4)
        synchronized (this) {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
                System.out.println("Stopped timer for: " + currentApp);
            }
        }

        currentApp = newApp;
        secondsElapsed = 0;
        timerLabel.setText("00:00:00");
        screenProgress.setProgress(0);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            secondsElapsed++;
            Platform.runLater(() -> {
                int hours = secondsElapsed / 3600;
                int minutes = (secondsElapsed % 3600) / 60;
                int secs = secondsElapsed % 60;

                timerLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, secs));
                screenProgress.setProgress((double) secondsElapsed / DEMO_LIMIT_SECONDS);

                // Trigger Alert (Week 4)
                if (secondsElapsed >= DEMO_LIMIT_SECONDS) {
                    showAlert("Limit Reached!", "You have reached your limit for " + currentApp);
                    scheduler.shutdownNow();
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}