package application;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.ActivityEntry;
import model.Goal;
import model.SleepLog;
import service.SessionCounter;
import service.StatsCalculator;
import service.ThreadManager;
import service.TimerThread;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainWindowController {

    // SCREEN TIME
    @FXML private TabPane mainTabPane;
    @FXML private ComboBox<String> appComboBox;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private TextField searchField;
    @FXML private Label timerLabel;
    @FXML private Label tipLabel;
    @FXML private ProgressBar screenProgress;
    @FXML private TableView<ActivityEntry> activityTable;
    @FXML private TableColumn<ActivityEntry, String> nameColumn;
    @FXML private TableColumn<ActivityEntry, String> categoryColumn;
    @FXML private TableColumn<ActivityEntry, String> durationColumn;
    @FXML private PieChart activityPieChart;

    @FXML private Button youtubeIcon;
    @FXML private Button facebookIcon;
    @FXML private Button instagramIcon;
    @FXML private Button whatsappIcon;
    @FXML private Button chromeIcon;

    @FXML private StackPane screenTimeStack;
    @FXML private VBox screenTimeContent;
    @FXML private VBox previewOverlay;
    @FXML private Button previewBackBtn;
    @FXML private Label previewAppNameLabel;
    @FXML private Label previewTimerLabel;
    @FXML private VBox previewHeader;
    @FXML private Label previewContentTitle;

    // STUDY
    @FXML private TextField studySubjectField;
    @FXML private Label studyTimerLabel;
    @FXML private Button studyToggleButton;
    @FXML private Label studyTotalLabel;
    @FXML private Label studySessionsLabel;

    // SLEEP
    @FXML private ComboBox<String> bedHourCombo;
    @FXML private ComboBox<String> bedMinuteCombo;
    @FXML private ComboBox<String> wakeHourCombo;
    @FXML private ComboBox<String> wakeMinuteCombo;
    @FXML private Label sleepResultLabel;

    // SIDEBAR + HEADER
    @FXML private Button focusModeButton;
    @FXML private Button darkModeBtn;
    @FXML private Button goalsBtn;
    @FXML private Button historyBtn;
    @FXML private Button achievementsBtn;
    @FXML private Button reportsBtn;
    @FXML private Button settingsBtn;
    @FXML private Button threadMonitorBtn;
    @FXML private Button dateBoxBtn;
    @FXML private Button notificationBtn;
    @FXML private Button profileBtn;
    @FXML private Button infoButton;
    @FXML private Button prevTipBtn;
    @FXML private Button nextTipBtn;
    @FXML private Button editGoalsBtn;
    @FXML private Label scoreNumber;
    @FXML private Label scoreStatus;
    @FXML private Label scoreMessage;

    // GOAL PANEL
    @FXML private ProgressBar goalStudyBar;
    @FXML private Label goalStudyValue;
    @FXML private ProgressBar goalScreenBar;
    @FXML private Label goalScreenValue;
    @FXML private ProgressBar goalSleepBar;
    @FXML private Label goalSleepValue;

    // BOTTOM BAR
    @FXML private Label streakLabel;
    @FXML private Label achieveLabel;
    @FXML private Label sessionsLabel;
    @FXML private Label balanceLabel;
    @FXML private Label balanceSubLabel;

    // ===================================================
    //  STATE
    // ===================================================
    private TimerThread screenTimer;   // Week 4: extends Thread
    private int screenSeconds = 0;
    private String currentApp = "";
    private boolean focusMode = false;
    private boolean inPreviewMode = false;

    private TimerThread studyTimer;
    private int studySeconds = 0;
    private boolean studyRunning = false;
    private int totalStudyMinutes = 0;
    private int totalSessionsDone = 0;

    private boolean darkMode = false;

    private final ObservableList<ActivityEntry> activityLog = FXCollections.observableArrayList();
    private final FilteredList<ActivityEntry> filteredLog = new FilteredList<>(activityLog, p -> true);

    private final ObservableList<Goal> goals = FXCollections.observableArrayList();
    private final ObservableList<String> achievements = FXCollections.observableArrayList();

    private final List<String> tips = new ArrayList<>();
    private int currentTipIndex = 0;
    private final List<String> notifications = new ArrayList<>();

    private final ThreadManager threadManager = ThreadManager.getInstance();

    // ===================================================
    //  INIT
    // ===================================================
    @FXML
    public void initialize() {
        setupScreenTab();
        setupStudyTab();
        setupSleepTab();
        setupPieChart();
        setupTips();
        setupNotifications();

        // Week 4: start the consumer thread that reads from SessionQueue
        threadManager.startQueueConsumer(() -> {
            updateScore();
            updateGoalPanel();
            updatePieChart();
            updateBottomBar();
        });

        updateScore();
        updateGoalPanel();
        updateBottomBar();
        refreshAchievements();
    }

    private void setupScreenTab() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("durationFormatted"));
        activityTable.setItems(filteredLog);

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        filterComboBox.getItems().addAll("All", "Screen Time", "Study Time", "Sleep Time");
        filterComboBox.setValue("All");
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

    private void setupStudyTab() { studyTimerLabel.setText("00:00:00"); }

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

    private void setupPieChart() { updatePieChart(); }

    private void setupTips() {
        tips.add("Try the 20-20-20 rule: Every 20 minutes, look at something 20 feet away.");
        tips.add("Taking a 5-minute walk every hour improves focus by up to 30%.");
        tips.add("Avoid screens 1 hour before bedtime for deeper sleep.");
        tips.add("Pomodoro: Work in focused blocks with short breaks.");
        tips.add("Drinking water regularly keeps your brain sharp.");
        tipLabel.setText("Tip: " + tips.get(0));
    }

    private void setupNotifications() {
        notifications.add("Welcome! Set a goal to see your productivity score.");
        notifications.add("Tip: Click an app icon to start tracking.");
    }

    // ===================================================
    //  FILTERS
    // ===================================================
    private void applyFilters() {
        String s = (searchField.getText() == null) ? "" : searchField.getText().toLowerCase().trim();
        String cat = filterComboBox.getValue();
        filteredLog.setPredicate(entry -> {
            boolean ms = s.isEmpty() || entry.getName().toLowerCase().contains(s);
            boolean mc = (cat == null) || "All".equals(cat) || cat.equals(entry.getCategory());
            return ms && mc;
        });
    }

    @FXML private void refreshTable() {
        searchField.clear();
        filterComboBox.setValue("All");
        applyFilters();
    }

    // ===================================================
    //  SESSION HANDLING (uses ThreadManager + queue)
    // ===================================================
    private void addOrMergeEntry(String name, String category, int seconds) {
        if (seconds <= 0) return;
        for (ActivityEntry e : activityLog) {
            if (e.getName().equals(name) && e.getCategory().equals(category)) {
                e.addDuration(seconds);
                activityTable.refresh();
                return;
            }
        }
        activityLog.add(new ActivityEntry(name, category, seconds));
        activityTable.refresh();
    }

    // ===================================================
    //  PREVIEW / TIMER
    // ===================================================
    private void openAppPreview(String appName) {
        currentApp = appName;
        screenSeconds = 0;
        inPreviewMode = true;
        applyPreviewTheme(appName);
        previewAppNameLabel.setText(appName);
        previewTimerLabel.setText("00:00:00");
        screenTimeContent.setVisible(false);
        screenTimeContent.setManaged(false);
        previewOverlay.setVisible(true);
        previewOverlay.setManaged(true);

        // Week 4: start a Thread (extends Thread)
        screenTimer = new TimerThread("screen-timer", new TimerThread.TickListener() {
            @Override public void onTick(int s) {
                screenSeconds = s;
                previewTimerLabel.setText(formatTime(s));
                screenProgress.setProgress(Math.min((double) s / (60 * 60), 1.0));
            }
            @Override public void onFinish(int s) {
                System.out.println("[" + Thread.currentThread().getName() + "] timer finished");
            }
        });
        screenTimer.start();
    }

    private void applyPreviewTheme(String appName) {
        String accent, content;
        switch (appName) {
            case "YouTube":   accent = "#FF0000"; content = "▶ YouTube\n\n• Trending videos\n• Subscriptions\n\n(Simulated)"; break;
            case "Facebook":  accent = "#1877F2"; content = "f Facebook\n\n• News feed\n• Friends\n\n(Simulated)"; break;
            case "Instagram": accent = "#E1306C"; content = "◎ Instagram\n\n• Stories\n• Reels\n\n(Simulated)"; break;
            case "WhatsApp":  accent = "#25D366"; content = "● WhatsApp\n\n• Family group\n• Chats\n\n(Simulated)"; break;
            case "Chrome":    accent = "#4285F4"; content = "C Chrome\n\n• Tabs\n• Search\n\n(Simulated)"; break;
            default:          accent = "#1769ff"; content = "(Simulated)";
        }
        previewHeader.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 12 12 0 0; -fx-padding: 20;");
        previewAppNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        previewTimerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        previewBackBtn.setStyle("-fx-background-color: rgba(255,255,255,0.25); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 16;");
        previewContentTitle.setText(content);
    }

    private void endPreview() {
        // Week 4: cooperative stop via interrupt()
        if (screenTimer != null) {
            screenTimer.stopTimer();
            screenTimer = null;
        }

        if (screenSeconds >= 5) {
            ActivityEntry entry = new ActivityEntry(currentApp, "Screen Time", screenSeconds);
            addOrMergeEntry(currentApp, "Screen Time", screenSeconds);
            recordProgress("Screen Time", screenSeconds / 60);

            // Week 4: enqueue to producer-consumer queue for the background consumer thread
            try {
                threadManager.getQueue().put(entry.copy());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Session Ended");
            a.setHeaderText("Session for " + currentApp + " ended");
            a.setContentText("Time spent: " + formatTime(screenSeconds));
            a.showAndWait();
        }

        currentApp = "";
        screenSeconds = 0;
        inPreviewMode = false;
        previewOverlay.setVisible(false);
        previewOverlay.setManaged(false);
        screenTimeContent.setVisible(true);
        screenTimeContent.setManaged(true);
    }

    private String formatTime(int t) {
        return String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
    }

    // ===================================================
    //  STUDY
    // ===================================================
    @FXML private void toggleStudySession() {
        if (studyRunning) {
            studyRunning = false;
            if (studyTimer != null) { studyTimer.stopTimer(); studyTimer = null; }
            studyToggleButton.setText("▶   Start Focus Session");

            if (studySeconds >= 5) {
                String subject = studySubjectField.getText().isEmpty() ? "General Study" : studySubjectField.getText();
                addOrMergeEntry("📖 " + subject, "Study Time", studySeconds);
                totalStudyMinutes += studySeconds / 60;
                totalSessionsDone++;

                ActivityEntry entry = new ActivityEntry("📖 " + subject, "Study Time", studySeconds);
                try { threadManager.getQueue().put(entry); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                recordProgress("Study Time", studySeconds / 60);
                updateStudyStats();
            }
            studySeconds = 0;
            studyTimerLabel.setText("00:00:00");
        } else {
            studyRunning = true;
            studySeconds = 0;
            studyToggleButton.setText("⏸   Stop Session");
            studyTimer = new TimerThread("study-timer", new TimerThread.TickListener() {
                @Override public void onTick(int s) {
                    studySeconds = s;
                    studyTimerLabel.setText(formatTime(s));
                }
                @Override public void onFinish(int s) { }
            });
            studyTimer.start();
        }
    }

    private void updateStudyStats() {
        studyTotalLabel.setText(String.format("%dh %02dm", totalStudyMinutes / 60, totalStudyMinutes % 60));
        studySessionsLabel.setText(String.valueOf(totalSessionsDone));
    }

    // ===================================================
    //  SLEEP
    // ===================================================
    @FXML private void calculateSleep() {
        try {
            LocalTime bed = LocalTime.of(Integer.parseInt(bedHourCombo.getValue()), Integer.parseInt(bedMinuteCombo.getValue()));
            LocalTime wake = LocalTime.of(Integer.parseInt(wakeHourCombo.getValue()), Integer.parseInt(wakeMinuteCombo.getValue()));
            SleepLog log = new SleepLog(LocalDate.now(), bed, wake);
            long h = log.getHoursSlept();
            String suffix = h < 5 ? "  ⚠ Too little!" : (h > 9 ? "  ☕ Oversleeping?" : "  ✓ Healthy!");
            sleepResultLabel.setText(log.getFormattedDuration() + suffix);

            int sleepMinutes = (int) log.getTotalMinutes();
            // Replace any previous sleep entry
            activityLog.removeIf(e -> "Sleep Time".equals(e.getCategory()));
            if (sleepMinutes > 0) {
                activityLog.add(new ActivityEntry("💤 Sleep", "Sleep Time", sleepMinutes * 60));
                try {
                    threadManager.getQueue().put(new ActivityEntry("💤 Sleep", "Sleep Time", sleepMinutes * 60));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            activityTable.refresh();
            refreshAchievements();
            updateScore();
            updateGoalPanel();
            updatePieChart();
            updateBottomBar();
        } catch (Exception ex) { sleepResultLabel.setText("Invalid input"); }
    }

    // ===================================================
    //  GOAL + SCORE
    // ===================================================
    private void recordProgress(String category, int minutes) {
        if (minutes <= 0) return;
        for (Goal g : goals) if (g.getCategory().equals(category)) g.addProgress(minutes);
        refreshAchievements();
        updateScore();
        updateGoalPanel();
        updatePieChart();
        updateBottomBar();
    }

    private void refreshAchievements() {
        achievements.clear();
        for (Goal g : goals) if (g.isCompleted()) achievements.add("🏆 " + g.getName());
        if (achieveLabel != null) achieveLabel.setText(String.valueOf(achievements.size()));
    }

    private void updateScore() {
        int studyGoal = 0;
        for (Goal g : goals) if ("Study Time".equals(g.getCategory())) studyGoal += g.getTargetMinutes();
        if (studyGoal <= 0) {
            scoreNumber.setText("0");
            scoreStatus.setText("No study goal");
            scoreMessage.setText("Set a Study Goal");
            return;
        }
        int studyMinutes = 0, screenMinutes = 0;
        for (ActivityEntry e : activityLog) {
            if ("Study Time".equals(e.getCategory())) studyMinutes += e.getDurationSeconds() / 60;
            if ("Screen Time".equals(e.getCategory())) screenMinutes += e.getDurationSeconds() / 60;
        }
        double raw = ((double)(studyMinutes - screenMinutes) / studyGoal) * 100;
        int score = (int) Math.max(0, Math.min(100, Math.round(raw)));
        scoreNumber.setText(String.valueOf(score));
        if (score >= 80) scoreStatus.setText("Excellent Day! ⭐");
        else if (score >= 50) scoreStatus.setText("Good Day! ✓");
        else if (score >= 20) scoreStatus.setText("Keep Going 💪");
        else scoreStatus.setText("Just Starting");
        scoreMessage.setText(studyMinutes + "m study − " + screenMinutes + "m screen");
    }

    private void updateGoalPanel() {
        int studyT = 0, studyC = 0, screenT = 0, screenC = 0, sleepT = 0, sleepC = 0;
        for (Goal g : goals) {
            switch (g.getCategory()) {
                case "Study Time":  studyT += g.getTargetMinutes();  studyC += g.getCurrentMinutes();  break;
                case "Screen Time": screenT += g.getTargetMinutes(); screenC += g.getCurrentMinutes(); break;
                case "Sleep Time":  sleepT += g.getTargetMinutes();  sleepC += g.getCurrentMinutes();  break;
            }
        }
        if (studyT > 0) { goalStudyBar.setProgress(Math.min(1.0, (double) studyC / studyT)); goalStudyValue.setText(studyC + "m / " + studyT + "m"); }
        else { goalStudyBar.setProgress(0); goalStudyValue.setText("No goal"); }
        if (screenT > 0) { goalScreenBar.setProgress(Math.min(1.0, (double) screenC / screenT)); goalScreenValue.setText(screenC + "m / " + screenT + "m"); }
        else { goalScreenBar.setProgress(0); goalScreenValue.setText("No goal"); }
        if (sleepT > 0) { goalSleepBar.setProgress(Math.min(1.0, (double) sleepC / sleepT)); goalSleepValue.setText(sleepC + "m / " + sleepT + "m"); }
        else { goalSleepBar.setProgress(0); goalSleepValue.setText("No goal"); }
    }

    private void updatePieChart() {
        int screen = 0, study = 0, sleep = 0;
        for (ActivityEntry e : activityLog) {
            if ("Screen Time".equals(e.getCategory())) screen += e.getDurationSeconds();
            if ("Study Time".equals(e.getCategory())) study += e.getDurationSeconds();
            if ("Sleep Time".equals(e.getCategory())) sleep += e.getDurationSeconds();
        }
        double total = 24 * 3600.0;
        double sp = screen / total * 100, stp = study / total * 100, slp = sleep / total * 100;
        double free = Math.max(0, 100 - sp - stp - slp);
        activityPieChart.getData().clear();
        activityPieChart.getData().addAll(
                new PieChart.Data(String.format("Screen %.0f%%", sp), Math.max(sp, 0.01)),
                new PieChart.Data(String.format("Study %.0f%%", stp), Math.max(stp, 0.01)),
                new PieChart.Data(String.format("Sleep %.0f%%", slp), Math.max(slp, 0.01)),
                new PieChart.Data(String.format("Free %.0f%%", free), Math.max(free, 0.01))
        );
    }

    private void updateBottomBar() {
        if (achieveLabel != null) achieveLabel.setText(String.valueOf(achievements.size()));
        if (sessionsLabel != null) sessionsLabel.setText(String.valueOf(activityLog.size()));
        int streak = activityLog.isEmpty() ? 0 : 1;
        if (streakLabel != null) streakLabel.setText(String.valueOf(streak));
        if (balanceLabel != null) {
            int score = 0;
            try { score = Integer.parseInt(scoreNumber.getText()); } catch (Exception ignored) {}
            if (score >= 80) { balanceLabel.setText("Great Balance!"); balanceSubLabel.setText("You're doing awesome!"); }
            else if (score >= 50) { balanceLabel.setText("On Track"); balanceSubLabel.setText("Keep it up!"); }
            else if (activityLog.isEmpty()) { balanceLabel.setText("Get Started"); balanceSubLabel.setText("Do an activity"); }
            else { balanceLabel.setText("Needs Focus"); balanceSubLabel.setText("Try less screen time"); }
        }
    }

    // ===================================================
    //  DARK MODE
    // ===================================================
    @FXML private void toggleDarkMode() {
        darkMode = !darkMode;
        Scene scene = screenTimeStack.getScene();
        if (darkMode) {
            if (!scene.getRoot().getStyleClass().contains("dark-mode"))
                scene.getRoot().getStyleClass().add("dark-mode");
            darkModeBtn.setText("☀");
        } else {
            scene.getRoot().getStyleClass().remove("dark-mode");
            darkModeBtn.setText("☾");
        }
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
    //  SIDEBAR
    // ===================================================
    @FXML private void goHome() { mainTabPane.getSelectionModel().select(0); }

    @FXML private void openGoalsWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Goals");
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");
        Label title = new Label("Your Goals");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");

        VBox goalList = new VBox(8);
        Label listTitle = new Label("Current Goals:");
        listTitle.setStyle("-fx-font-weight: bold;");
        goalList.getChildren().add(listTitle);
        renderGoalList(goalList);

        Separator sep = new Separator();
        Label addTitle = new Label("Add New Goal:");
        addTitle.setStyle("-fx-font-weight: bold;");
        TextField nameField = new TextField();
        nameField.setPromptText("Goal name");
        TextField targetField = new TextField();
        targetField.setPromptText("Target minutes");
        ComboBox<String> catField = new ComboBox<>();
        catField.getItems().addAll("Study Time", "Screen Time", "Sleep Time");
        catField.setValue("Study Time");
        Button addBtn = new Button("+ Add Goal");
        addBtn.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 6;");
        Label status = new Label("");

        addBtn.setOnAction(e -> {
            try {
                String n = nameField.getText().trim();
                int t = Integer.parseInt(targetField.getText().trim());
                if (n.isEmpty() || t <= 0) throw new Exception();
                goals.add(new Goal(n, t, catField.getValue()));
                nameField.clear(); targetField.clear();
                goalList.getChildren().clear();
                goalList.getChildren().add(listTitle);
                renderGoalList(goalList);
                status.setText("✓ Goal added!");
                status.setStyle("-fx-text-fill: green;");
                refreshAchievements(); updateScore(); updateGoalPanel(); updateBottomBar();
            } catch (Exception ex) {
                status.setText("Please enter valid values.");
                status.setStyle("-fx-text-fill: red;");
            }
        });

        root.getChildren().addAll(title, goalList, sep, addTitle, nameField, targetField, catField, addBtn, status);
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        stage.setScene(new Scene(sp, 520, 620));
        stage.show();
    }

    private void renderGoalList(VBox container) {
        if (goals.isEmpty()) {
            Label empty = new Label("No goals yet.");
            empty.setStyle("-fx-text-fill: #68738a;");
            container.getChildren().add(empty);
            return;
        }
        for (Goal g : goals) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 6; -fx-border-color: #dce4ef; -fx-border-radius: 6;");
            String marker = g.isCompleted() ? "  ✓" : "";
            Label info = new Label(g.getName() + " (" + g.getCategory() + ")\n" + g.getProgressText() + marker);
            info.setStyle("-fx-font-size: 13px;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button del = new Button("Delete");
            del.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4;");
            del.setOnAction(e -> {
                goals.remove(g);
                container.getChildren().clear();
                Label lt = new Label("Current Goals:");
                lt.setStyle("-fx-font-weight: bold;");
                container.getChildren().add(lt);
                renderGoalList(container);
                refreshAchievements(); updateScore(); updateGoalPanel(); updateBottomBar();
            });
            row.getChildren().addAll(info, spacer, del);
            container.getChildren().add(row);
        }
    }

    @FXML private void openHistoryWindow() {
        StringBuilder sb = new StringBuilder();
        if (activityLog.isEmpty()) sb.append("No activity recorded yet.");
        else for (ActivityEntry e : activityLog)
            sb.append("• ").append(e.getName()).append(" — ").append(e.getDurationFormatted())
                    .append(" (").append(e.getCategory()).append(")\n");
        openInfoWindow("History", "Today's Session History", sb.toString());
    }

    @FXML private void openAchievementsWindow() {
        StringBuilder sb = new StringBuilder();
        if (achievements.isEmpty()) sb.append("No achievements yet.\nComplete a goal to unlock one!");
        else for (String a : achievements) sb.append(a).append("\n");
        openInfoWindow("Achievements", "Your Achievements", sb.toString());
    }

    // Week 4: Reports now uses Future + Callable
    @FXML private void openReportsWindow() {
        try {
            List<ActivityEntry> snapshot = new ArrayList<>(activityLog);
            Future<StatsCalculator.Stats> future =
                    threadManager.submitCalculation(new StatsCalculator(snapshot));

            // We can do other work here before blocking on future.get()
            long completed = goals.stream().filter(Goal::isCompleted).count();

            // Block for the result
            StatsCalculator.Stats stats = future.get();

            openInfoWindow("Reports", "Today's Report",
                    stats.toString() + "\n" +
                            "Goals Completed: " + completed + " / " + goals.size() + "\n" +
                            "Productivity Score: " + scoreNumber.getText() + "%");
        } catch (Exception ex) {
            openInfoWindow("Reports", "Error", "Could not compute stats: " + ex.getMessage());
        }
    }

    // Week 4: Thread Monitor window (visualizes thread states, race vs. synchronized)
    @FXML private void openThreadMonitor() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/ThreadMonitor.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Thread Monitor — Week 4 Demo");
            stage.initModality(Modality.NONE);
            stage.setScene(new Scene(root, 780, 620));
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML private void openSettingsWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Settings");
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");
        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");
        Label prefTitle = new Label("Preferences");
        prefTitle.setStyle("-fx-font-weight: bold;");
        CheckBox notifChk = new CheckBox("Enable notifications"); notifChk.setSelected(true);
        CheckBox soundChk = new CheckBox("Enable sound alerts");
        CheckBox autoChk = new CheckBox("Auto-start in Focus Mode");

        Button manageGoalsBtn = new Button("⚙  Manage Goals...");
        manageGoalsBtn.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        manageGoalsBtn.setOnAction(e -> { stage.close(); openGoalsWindow(); });

        Button resetBtn = new Button("🗑  Reset Today's Data");
        resetBtn.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        resetBtn.setOnAction(e -> {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Reset today's activities?",
                    ButtonType.OK, ButtonType.CANCEL);
            if (c.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                activityLog.clear();
                achievements.clear();
                goals.clear();
                totalStudyMinutes = 0; totalSessionsDone = 0;
                threadManager.getCounter().reset();
                updateStudyStats(); refreshAchievements(); updateScore();
                updateGoalPanel(); updatePieChart(); updateBottomBar();
            }
        });

        Label aboutTitle = new Label("About"); aboutTitle.setStyle("-fx-font-weight: bold;");
        Label about = new Label("Personalized Activity and Screen Time Manager\nv1.0 — Lab Project (Weeks 1–4)");
        root.getChildren().addAll(title, prefTitle, notifChk, soundChk, autoChk,
                new Separator(), manageGoalsBtn, resetBtn, new Separator(), aboutTitle, about);
        stage.setScene(new Scene(root, 480, 480));
        stage.show();
    }

    // ===================================================
    //  HEADER
    // ===================================================
    @FXML private void showDate() { openInfoWindow("Today", "Current Date", "Today is " + LocalDate.now()); }

    @FXML private void showNotifications() {
        StringBuilder sb = new StringBuilder();
        for (String n : notifications) sb.append("• ").append(n).append("\n\n");
        openInfoWindow("Notifications", "Notifications", sb.toString());
    }

    @FXML private void showProfile() {
        long completed = goals.stream().filter(Goal::isCompleted).count();
        openInfoWindow("Profile", "User Profile",
                "Name: Student\nRole: University Student\nTotal Goals: " + goals.size() +
                        "\nCompleted: " + completed + "\nAchievements: " + achievements.size());
    }

    // ===================================================
    //  RIGHT PANEL
    // ===================================================
    @FXML private void showPieInfo() {
        openInfoWindow("Chart Info", "24-Hour Breakdown",
                "This chart shows your tracked time distribution.\nIt updates automatically.");
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
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");
        Label h = new Label(header);
        h.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");
        TextArea area = new TextArea(content);
        area.setEditable(false); area.setWrapText(true); area.setPrefHeight(320);
        area.setStyle("-fx-font-size: 14px;");
        root.getChildren().addAll(h, area);
        stage.setScene(new Scene(root, 520, 440));
        stage.show();
    }
}