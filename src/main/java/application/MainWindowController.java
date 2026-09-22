package application;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.App;
import model.SleepLog;
import model.StudySession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainWindowController {

    // ===================================================
    //  SCREEN TIME TAB
    // ===================================================
    @FXML private TabPane mainTabPane;
    @FXML private ComboBox<String> appComboBox;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private TextField searchField;
    @FXML private Label timerLabel;
    @FXML private Label tipLabel;
    @FXML private ProgressBar screenProgress;
    @FXML private TableView<App> activityTable;
    @FXML private TableColumn<App, String> appNameColumn;
    @FXML private TableColumn<App, Integer> durationColumn;
    @FXML private TableColumn<App, String> categoryColumn;
    @FXML private PieChart activityPieChart;

    // App icons
    @FXML private Button youtubeIcon;
    @FXML private Button facebookIcon;
    @FXML private Button instagramIcon;
    @FXML private Button whatsappIcon;
    @FXML private Button chromeIcon;

    // Preview overlay (inside Screen Time tab)
    @FXML private StackPane screenTimeStack;
    @FXML private VBox screenTimeContent;
    @FXML private VBox previewOverlay;
    @FXML private Button previewBackBtn;
    @FXML private Label previewAppNameLabel;
    @FXML private Label previewTimerLabel;
    @FXML private VBox previewHeader;
    @FXML private Label previewContentTitle;

    // ===================================================
    //  STUDY TIME TAB
    // ===================================================
    @FXML private TextField studySubjectField;
    @FXML private Label studyTimerLabel;
    @FXML private ProgressBar studyProgress;
    @FXML private Button studyToggleButton;
    @FXML private Label studyTotalLabel;
    @FXML private Label studySessionsLabel;

    // ===================================================
    //  SLEEP TIME TAB
    // ===================================================
    @FXML private ComboBox<String> bedHourCombo;
    @FXML private ComboBox<String> bedMinuteCombo;
    @FXML private ComboBox<String> wakeHourCombo;
    @FXML private ComboBox<String> wakeMinuteCombo;
    @FXML private Label sleepResultLabel;

    // ===================================================
    //  SIDEBAR + HEADER
    // ===================================================
    @FXML private Button focusModeButton;
    @FXML private Button homeBtn;
    @FXML private Button studyTimeBtn;
    @FXML private Button sleepTimeBtn;
    @FXML private Button goalsBtn;
    @FXML private Button historyBtn;
    @FXML private Button achievementsBtn;
    @FXML private Button reportsBtn;
    @FXML private Button settingsBtn;
    @FXML private Button dateBoxBtn;
    @FXML private Button notificationBtn;
    @FXML private Button profileBtn;
    @FXML private Button infoButton;
    @FXML private Button prevTipBtn;
    @FXML private Button nextTipBtn;
    @FXML private Button editGoalsBtn;

    // ===================================================
    //  STATE
    // ===================================================
    private ScheduledExecutorService screenScheduler;
    private int screenSeconds = 0;
    private String currentApp = "";
    private int currentLimitSeconds = 60;
    private boolean focusMode = false;
    private int lastWarningAt = -1;
    private boolean inPreviewMode = false;

    private ScheduledExecutorService studyScheduler;
    private int studySeconds = 0;
    private final int POMODORO_SECONDS = 25 * 60;
    private boolean studyRunning = false;
    private int totalStudyMinutes = 0;
    private int sessionsCompleted = 0;

    private int dailyScreenLimitMinutes = 360;
    private int dailyStudyGoalMinutes = 300;
    private int dailySleepGoalMinutes = 480;

    private ObservableList<App> masterData = FXCollections.observableArrayList();
    private FilteredList<App> filteredData = new FilteredList<>(masterData, p -> true);

    private final List<String> tips = new ArrayList<>();
    private int currentTipIndex = 0;
    private final List<String> sessionHistory = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();

    // ===================================================
    //  INIT
    // ===================================================
    @FXML
    public void initialize() {
        setupScreenTimeTab();
        setupStudyTab();
        setupSleepTab();
        setupPieChart();
        setupTips();
        setupNotifications();
    }

    private void setupScreenTimeTab() {
        appNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("totalSecondsUsed"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        activityTable.setItems(filteredData);

        masterData.addAll(
                new App("YouTube", 60, "Screen Time"),
                new App("Facebook", 30, "Screen Time"),
                new App("Instagram", 45, "Screen Time"),
                new App("WhatsApp", 60, "Screen Time"),
                new App("Chrome", 120, "Screen Time"),
                new App("DSA Practice", 120, "Study Time")
        );

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        filterComboBox.getItems().addAll("All Apps", "Screen Time", "Study Time");
        filterComboBox.setValue("All Apps");
        filterComboBox.setOnAction(e -> applyFilters());

        appComboBox.getItems().addAll("YouTube", "Facebook", "Instagram", "WhatsApp", "Chrome");
        appComboBox.setPromptText("Select App...");
        appComboBox.setOnAction(e -> {
            String v = appComboBox.getValue();
            if (v != null) { openAppPreview(v); appComboBox.setValue(null); }
        });

        previewBackBtn.setOnAction(e -> endPreview());
        youtubeIcon.setOnAction(e -> openAppPreview("YouTube"));
        facebookIcon.setOnAction(e -> openAppPreview("Facebook"));
        instagramIcon.setOnAction(e -> openAppPreview("Instagram"));
        whatsappIcon.setOnAction(e -> openAppPreview("WhatsApp"));
        chromeIcon.setOnAction(e -> openAppPreview("Chrome"));
    }

    private void setupStudyTab() {
        studyTimerLabel.setText("25:00");
        studyProgress.setProgress(0);
    }

    private void setupSleepTab() {
        for (int i = 0; i < 24; i++) {
            String h = String.format("%02d", i);
            bedHourCombo.getItems().add(h);
            wakeHourCombo.getItems().add(h);
        }
        for (int i = 0; i < 60; i++) {
            String m = String.format("%02d", i);
            bedMinuteCombo.getItems().add(m);
            wakeMinuteCombo.getItems().add(m);
        }
        bedHourCombo.setValue("23"); bedMinuteCombo.setValue("00");
        wakeHourCombo.setValue("07"); wakeMinuteCombo.setValue("00");
    }

    private void setupPieChart() {
        activityPieChart.getData().addAll(
                new PieChart.Data("Screen Time", 25),
                new PieChart.Data("Study Time", 25),
                new PieChart.Data("Sleep Time", 30),
                new PieChart.Data("Free Time", 20)
        );
    }

    private void setupTips() {
        tips.add("Try the 20-20-20 rule: Every 20 minutes, look at something 20 feet away for 20 seconds.");
        tips.add("Taking a 5-minute walk every hour improves focus by up to 30%.");
        tips.add("Avoid screens 1 hour before bedtime for deeper sleep.");
        tips.add("Pomodoro: Work 25 min, rest 5. Repeat 4 times, then take a longer break.");
        tips.add("Drinking water regularly keeps your brain sharp.");
        tipLabel.setText("Tip: " + tips.get(0));
    }

    private void setupNotifications() {
        notifications.add("Welcome back! You have a 7-day streak.");
        notifications.add("You hit 82% productivity yesterday. Great work!");
        notifications.add("Reminder: Take a break every 45 minutes.");
    }

    // ===================================================
    //  FILTERS
    // ===================================================
    private void applyFilters() {
        String s = (searchField.getText() == null) ? "" : searchField.getText().toLowerCase().trim();
        String cat = filterComboBox.getValue();
        filteredData.setPredicate(app -> {
            boolean ms = s.isEmpty() || app.getName().toLowerCase().contains(s);
            boolean mc = (cat == null) || "All Apps".equals(cat) || cat.equals(app.getCategory());
            return ms && mc;
        });
    }

    @FXML private void refreshTable() {
        searchField.clear();
        filterComboBox.setValue("All Apps");
        applyFilters();
        activityTable.refresh();
    }

    // ===================================================
    //  APP PREVIEW
    // ===================================================
    private void openAppPreview(String appName) {
        if (screenScheduler != null && !screenScheduler.isShutdown()) {
            screenScheduler.shutdownNow();
            saveSession(currentApp, screenSeconds);
        }

        currentApp = appName;
        screenSeconds = 0;
        lastWarningAt = -1;
        inPreviewMode = true;

        currentLimitSeconds = 60 * 60;
        for (App app : masterData) {
            if (app.getName().equals(appName)) {
                currentLimitSeconds = app.getDailyLimitMinutes() * 60;
                break;
            }
        }

        applyPreviewTheme(appName);
        previewAppNameLabel.setText(appName);
        previewTimerLabel.setText("00:00:00");
        timerLabel.setText("00:00:00");
        screenProgress.setProgress(0);

        screenTimeContent.setVisible(false);
        screenTimeContent.setManaged(false);
        previewOverlay.setVisible(true);
        previewOverlay.setManaged(true);

        startScreenTimer();
    }

    private void applyPreviewTheme(String appName) {
        String accent, fakeContent;
        switch (appName) {
            case "YouTube":   accent = "#FF0000"; fakeContent = "▶ YouTube\n\n• Trending videos\n• Your subscriptions\n• Recommended content\n\n(Simulated session)"; break;
            case "Facebook":  accent = "#1877F2"; fakeContent = "f Facebook\n\n• News feed\n• Friends' posts\n• Groups you follow\n\n(Simulated session)"; break;
            case "Instagram": accent = "#E1306C"; fakeContent = "◎ Instagram\n\n• Stories\n• Reels\n• Explore page\n\n(Simulated session)"; break;
            case "WhatsApp":  accent = "#25D366"; fakeContent = "● WhatsApp\n\n• Family group\n• Study group\n• Recent chats\n\n(Simulated session)"; break;
            case "Chrome":    accent = "#4285F4"; fakeContent = "C Chrome\n\n• Open tabs\n• Search results\n• Bookmarks\n\n(Simulated session)"; break;
            default:          accent = "#1769ff"; fakeContent = "(Simulated session)";
        }
        previewHeader.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 12 12 0 0; -fx-padding: 20;");
        previewAppNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        previewTimerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        previewBackBtn.setStyle("-fx-background-color: rgba(255,255,255,0.25); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 16; -fx-cursor: hand;");
        previewContentTitle.setText(fakeContent);
    }

    private void endPreview() {
        if (screenScheduler != null && !screenScheduler.isShutdown()) screenScheduler.shutdownNow();
        saveSession(currentApp, screenSeconds);

        String spent = formatTime(screenSeconds);
        if (screenSeconds > 0) sessionHistory.add(currentApp + " - " + spent);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Session Ended");
        alert.setHeaderText("Session for " + currentApp + " ended");
        alert.setContentText("Time spent: " + spent);
        alert.showAndWait();

        currentApp = "";
        screenSeconds = 0;
        inPreviewMode = false;
        timerLabel.setText("00:00:00");
        screenProgress.setProgress(0);

        previewOverlay.setVisible(false);
        previewOverlay.setManaged(false);
        screenTimeContent.setVisible(true);
        screenTimeContent.setManaged(true);
    }

    private void saveSession(String appName, int seconds) {
        if (appName == null || appName.isEmpty() || seconds <= 0) return;
        for (App app : masterData) {
            if (app.getName().equals(appName)) {
                app.setTotalSecondsUsed(app.getTotalSecondsUsed() + seconds);
                break;
            }
        }
        activityTable.refresh();
    }

    // ===================================================
    //  TIMER
    // ===================================================
    private void startScreenTimer() {
        screenScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r); t.setDaemon(true); return t;
        });
        screenScheduler.scheduleAtFixedRate(() -> {
            try {
                if (focusMode) return;
                screenSeconds++;
                Platform.runLater(() -> {
                    String f = formatTime(screenSeconds);
                    if (inPreviewMode) previewTimerLabel.setText(f);
                    else timerLabel.setText(f);
                    screenProgress.setProgress(Math.min((double) screenSeconds / currentLimitSeconds, 1.0));

                    int warn = (int)(currentLimitSeconds * 0.6);
                    if (warn > 0 && screenSeconds == warn && lastWarningAt != warn) {
                        lastWarningAt = warn;
                        showAlert("Heads Up", "60% of your " + currentApp + " limit used.");
                    }
                    if (screenSeconds >= currentLimitSeconds) {
                        showAlert("Limit Reached!", "Reached limit for " + currentApp);
                        if (screenScheduler != null) screenScheduler.shutdownNow();
                    }
                });
            } catch (Exception ex) { ex.printStackTrace(); }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private String formatTime(int t) {
        return String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
    }

    // ===================================================
    //  FOCUS MODE
    // ===================================================
    @FXML private void toggleFocusMode() {
        focusMode = !focusMode;
        if (focusMode) {
            focusModeButton.setText("⊕    Focus Mode: ON");
            focusModeButton.setStyle("-fx-background-color: #e8f0ff; -fx-text-fill: #0758ed; -fx-font-weight: bold;");
        } else {
            focusModeButton.setText("⊕    Focus Mode");
            focusModeButton.setStyle("");
        }
    }

    // ===================================================
    //  STUDY TAB
    // ===================================================
    @FXML private void toggleStudySession() {
        if (studyRunning) {
            studyRunning = false;
            if (studyScheduler != null) studyScheduler.shutdownNow();
            studyToggleButton.setText("▶   Start Study Session");
            int m = studySeconds / 60;
            if (m > 0) {
                String subj = studySubjectField.getText().isEmpty() ? "General" : studySubjectField.getText();
                new StudySession(subj, m, LocalDate.now());
                totalStudyMinutes += m;
                sessionsCompleted++;
                updateStudyStats();
                sessionHistory.add("Study: " + subj + " - " + m + "m");
            }
            studySeconds = 0;
            studyTimerLabel.setText("25:00");
            studyProgress.setProgress(0);
        } else {
            studyRunning = true;
            studySeconds = 0;
            studyToggleButton.setText("⏸   Stop Session");
            studyScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r); t.setDaemon(true); return t;
            });
            studyScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (!studyRunning) return;
                    studySeconds++;
                    Platform.runLater(() -> {
                        int rem = Math.max(0, POMODORO_SECONDS - studySeconds);
                        studyTimerLabel.setText(String.format("%02d:%02d", rem / 60, rem % 60));
                        studyProgress.setProgress((double) studySeconds / POMODORO_SECONDS);
                        if (studySeconds >= POMODORO_SECONDS) studyToggleButton.fire();
                    });
                } catch (Exception ex) { ex.printStackTrace(); }
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void updateStudyStats() {
        studyTotalLabel.setText(String.format("%dh %02dm", totalStudyMinutes / 60, totalStudyMinutes % 60));
        studySessionsLabel.setText(String.valueOf(sessionsCompleted));
    }

    // ===================================================
    //  SLEEP TAB
    // ===================================================
    @FXML private void calculateSleep() {
        try {
            LocalTime bed = LocalTime.of(Integer.parseInt(bedHourCombo.getValue()), Integer.parseInt(bedMinuteCombo.getValue()));
            LocalTime wake = LocalTime.of(Integer.parseInt(wakeHourCombo.getValue()), Integer.parseInt(wakeMinuteCombo.getValue()));
            SleepLog log = new SleepLog(LocalDate.now(), bed, wake);
            long h = log.getHoursSlept();
            String suffix = h < 5 ? "  ⚠ Too little!" : (h > 9 ? "  ☕ Oversleeping?" : "  ✓ Healthy!");
            sleepResultLabel.setText(log.getFormattedDuration() + suffix);
        } catch (Exception ex) { sleepResultLabel.setText("Invalid input"); }
    }

    // ===================================================
    //  SIDEBAR - OPEN SEPARATE WINDOWS
    // ===================================================
    @FXML private void goHome() { mainTabPane.getSelectionModel().select(0); }
    @FXML private void openStudyWindow() { mainTabPane.getSelectionModel().select(1); }
    @FXML private void openSleepWindow() { mainTabPane.getSelectionModel().select(2); }
    @FXML private void openGoalsWindow() { openInfoWindow("Goals", "Daily Goals",
            "Study Goal: " + dailyStudyGoalMinutes + " min\n" +
                    "Screen Limit: " + dailyScreenLimitMinutes + " min\n" +
                    "Sleep Goal: " + dailySleepGoalMinutes + " min\n\n" +
                    "Edit goals via Settings."); }
    @FXML private void openHistoryWindow() {
        StringBuilder sb = new StringBuilder();
        if (sessionHistory.isEmpty()) sb.append("No sessions yet.");
        else for (String s : sessionHistory) sb.append("• ").append(s).append("\n");
        openInfoWindow("History", "Session History", sb.toString());
    }
    @FXML private void openAchievementsWindow() {
        int totalScreen = masterData.stream().mapToInt(App::getTotalSecondsUsed).sum();
        StringBuilder sb = new StringBuilder();
        sb.append(sessionsCompleted >= 1 ? "🏆 " : "🔒 ").append("First Study Session\n\n");
        sb.append(sessionsCompleted >= 3 ? "🏆 " : "🔒 ").append("3 Study Sessions\n\n");
        sb.append(totalScreen > 0 ? "🏆 " : "🔒 ").append("First Screen Session\n\n");
        sb.append(totalStudyMinutes >= 60 ? "🏆 " : "🔒 ").append("1 Hour Studied\n");
        openInfoWindow("Achievements", "Your Achievements", sb.toString());
    }
    @FXML private void openReportsWindow() {
        int totalScreen = masterData.stream().mapToInt(App::getTotalSecondsUsed).sum();
        openInfoWindow("Reports", "Today's Report",
                "Screen Time: " + formatTime(totalScreen) + "\n" +
                        "Study Time: " + totalStudyMinutes + " min\n" +
                        "Sessions: " + sessionsCompleted + "\n" +
                        "Focus Mode: " + (focusMode ? "ON" : "OFF"));
    }
    @FXML private void openSettingsWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Settings");
        VBox root = new VBox(15); root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");
        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");
        ToggleButton dark = new ToggleButton("Dark Mode");
        Label status = new Label("Light mode active");
        dark.setOnAction(e -> {
            if (dark.isSelected()) {
                root.getScene().getRoot().setStyle("-fx-base: #2c3e50; -fx-background: #1e2a38;");
                status.setText("Dark mode active");
            } else {
                root.getScene().getRoot().setStyle("");
                status.setText("Light mode active");
            }
        });
        root.getChildren().addAll(title, dark, status);
        stage.setScene(new Scene(root, 400, 250));
        stage.show();
    }

    // ===================================================
    //  HEADER
    // ===================================================
    @FXML private void showDate() {
        openInfoWindow("Today", "Current Date", "Today is " + LocalDate.now());
    }
    @FXML private void showNotifications() {
        StringBuilder sb = new StringBuilder();
        for (String n : notifications) sb.append("• ").append(n).append("\n\n");
        openInfoWindow("Notifications", "3 Notifications", sb.toString());
    }
    @FXML private void showProfile() {
        openInfoWindow("Profile", "User Profile",
                "Name: Student\nRole: University Student\nStreak: 7 days\nScore: 82");
    }

    // ===================================================
    //  RIGHT PANEL
    // ===================================================
    @FXML private void showPieInfo() {
        openInfoWindow("Chart Info", "24-Hour Breakdown",
                "This donut chart shows:\n• Screen Time\n• Study Time\n• Sleep Time\n• Free Time");
    }
    @FXML private void prevTip() {
        currentTipIndex = (currentTipIndex - 1 + tips.size()) % tips.size();
        tipLabel.setText("Tip: " + tips.get(currentTipIndex));
    }
    @FXML private void nextTip() {
        currentTipIndex = (currentTipIndex + 1) % tips.size();
        tipLabel.setText("Tip: " + tips.get(currentTipIndex));
    }
    @FXML private void openEditGoals() { openGoalsWindow(); }

    // ===================================================
    //  HELPERS
    // ===================================================
    private void openInfoWindow(String title, String header, String content) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        VBox root = new VBox(15); root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");
        Label h = new Label(header);
        h.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");
        TextArea area = new TextArea(content);
        area.setEditable(false); area.setWrapText(true); area.setPrefHeight(280);
        area.setStyle("-fx-font-size: 14px;");
        root.getChildren().addAll(h, area);
        stage.setScene(new Scene(root, 500, 400));
        stage.show();
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}