package application;

import database.ActivityDAO;
import database.CategorizationDAO;
import database.GoalDAO;
import javafx.animation.PauseTransition;
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
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import model.ActivityEntry;
import model.Goal;
import model.SleepLog;
import service.StatsCalculator;
import service.ThreadManager;
import service.TimerThread;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainWindowController {

    // ============================================================
    //  FXML fields
    // ============================================================
    @FXML private TabPane mainTabPane;
    @FXML private ComboBox<String> appComboBox, filterComboBox;
    @FXML private TextField searchField;
    @FXML private Label timerLabel, tipLabel;
    @FXML private ProgressBar screenProgress;
    @FXML private TableView<ActivityEntry> activityTable;
    @FXML private TableColumn<ActivityEntry, String> nameColumn, categoryColumn, durationColumn;
    @FXML private PieChart activityPieChart;
    @FXML private Button youtubeIcon, facebookIcon, instagramIcon, whatsappIcon, chromeIcon;
    @FXML private StackPane screenTimeStack;
    @FXML private VBox screenTimeContent, previewOverlay, previewHeader;
    @FXML private Button previewBackBtn;
    @FXML private Label previewAppNameLabel, previewTimerLabel, previewContentTitle;
    @FXML private TextField studySubjectField;
    @FXML private Label studyTimerLabel, studyTotalLabel, studySessionsLabel;
    @FXML private Button studyToggleButton;
    @FXML private ComboBox<String> bedHourCombo, bedMinuteCombo, wakeHourCombo, wakeMinuteCombo;
    @FXML private Label sleepResultLabel;
    @FXML private Button focusModeButton, darkModeBtn, goalsBtn, historyBtn, achievementsBtn,
            reportsBtn, settingsBtn, threadMonitorBtn, dateBoxBtn, notificationBtn,
            profileBtn, infoButton, prevTipBtn, nextTipBtn, editGoalsBtn;
    @FXML private Label scoreNumber, scoreStatus, scoreMessage;
    @FXML private ProgressBar goalStudyBar, goalScreenBar, goalSleepBar;
    @FXML private Label goalStudyValue, goalScreenValue, goalSleepValue;
    @FXML private Label streakLabel, achieveLabel, sessionsLabel, balanceLabel, balanceSubLabel;

    // ============================================================
    //  DAOs
    // ============================================================
    private final ActivityDAO activityDAO = new ActivityDAO();
    private final GoalDAO goalDAO = new GoalDAO();
    private final CategorizationDAO categorizationDAO = new CategorizationDAO();

    // ============================================================
    //  State
    // ============================================================
    private TimerThread screenTimer, studyTimer;
    private int screenSeconds = 0, studySeconds = 0;
    private int totalStudySeconds = 0;
    private int sessionsToday = 0;
    private String currentApp = "";
    private boolean focusMode = false, inPreviewMode = false, studyRunning = false, darkMode = false;

    private final ObservableList<ActivityEntry> activityLog = FXCollections.observableArrayList();
    private final FilteredList<ActivityEntry> filteredLog = new FilteredList<>(activityLog, p -> true);
    private final ObservableList<Goal> goals = FXCollections.observableArrayList();
    private final ObservableList<String> achievements = FXCollections.observableArrayList();

    private final Set<String> productiveApps = new LinkedHashSet<>();
    private final Set<String> nonProductiveApps = new LinkedHashSet<>();

    private final List<String> tips = new ArrayList<>();
    private int currentTipIndex = 0;
    private final List<String> notifications = new ArrayList<>();

    private final ThreadManager threadManager = ThreadManager.getInstance();
    private LocalDate lastActiveDate = LocalDate.now();
    private Stage threadMonitorStage = null;

    // Toast tracking
    private Set<String> previouslyCompletedGoals = new HashSet<>();

    // Day-rollover background check
    private final ScheduledExecutorService dayCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "day-rollover-check");
        t.setDaemon(true);
        return t;
    });

    // ============================================================
    //  INIT
    // ============================================================
    @FXML
    public void initialize() {
        setupDefaultCategorization();
        loadFromDatabase();
        setupScreenTab();
        setupStudyTab();
        setupSleepTab();
        setupTips();
        setupNotifications();

        threadManager.startQueueConsumer(() -> {
            // Consumer triggers a UI refresh after every consumed session
            refreshAchievements();
            updateScore();
            updateGoalPanel();
            updatePieChart();
            updateBottomBar();
        });

        refreshAchievements();
        updateScore();
        updateGoalPanel();
        updatePieChart();
        updateBottomBar();

        // Schedule day rollover check every 30 seconds
        dayCheckScheduler.scheduleAtFixedRate(() -> {
            if (!LocalDate.now().equals(lastActiveDate)) {
                Platform.runLater(this::performDayRollover);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void setupDefaultCategorization() {
        productiveApps.add("Chrome");
        nonProductiveApps.addAll(Arrays.asList("YouTube", "Facebook", "Instagram", "WhatsApp"));
    }

    private void loadFromDatabase() {
        try {
            activityLog.setAll(activityDAO.getTodayActivities());
            totalStudySeconds = 0;
            sessionsToday = 0;
            for (ActivityEntry e : activityLog) {
                if ("Study Time".equals(e.getCategory())) {
                    totalStudySeconds += e.getDurationSeconds();
                }
                sessionsToday++;
            }
        } catch (SQLException ex) {
            System.err.println("Failed to load activities: " + ex.getMessage());
        }

        try {
            goals.setAll(goalDAO.getTodayGoals());
        } catch (SQLException ex) {
            System.err.println("Failed to load goals: " + ex.getMessage());
        }

        try {
            Map<String, Boolean> cat = categorizationDAO.loadAll();
            // Merge with defaults so apps without DB entry keep default
            for (Map.Entry<String, Boolean> entry : cat.entrySet()) {
                if (entry.getValue()) {
                    productiveApps.add(entry.getKey());
                    nonProductiveApps.remove(entry.getKey());
                } else {
                    nonProductiveApps.add(entry.getKey());
                    productiveApps.remove(entry.getKey());
                }
            }
        } catch (SQLException ex) {
            System.err.println("Failed to load categorization: " + ex.getMessage());
        }

        // Initialise previously completed goals set (so we don't toast on load)
        for (Goal g : goals) if (g.isCompleted()) previouslyCompletedGoals.add(g.getName());
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

    private void setupStudyTab() {
        studyTimerLabel.setText("00:00:00");
        updateStudyStats();
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

    // ============================================================
    //  DAY ROLLOVER
    // ============================================================
    private void checkDayRollover() {
        if (!LocalDate.now().equals(lastActiveDate)) {
            performDayRollover();
        }
    }

    private void performDayRollover() {
        System.out.println("[Main] day changed - reloading from DB");
        lastActiveDate = LocalDate.now();

        // Reset goal progress for new day
        for (Goal g : goals) {
            g.resetProgress();
            try { goalDAO.updateProgress(g); } catch (SQLException ignored) {}
        }

        // Reload activities for new day
        try {
            activityLog.setAll(activityDAO.getTodayActivities());
        } catch (SQLException ex) {
            activityLog.clear();
        }

        totalStudySeconds = 0;
        sessionsToday = 0;
        previouslyCompletedGoals.clear();
        threadManager.getCounter().reset();
        threadManager.getQueue().clear();

        refreshAchievements();
        updateScore();
        updateGoalPanel();
        updatePieChart();
        updateBottomBar();
    }

    // ============================================================
    //  FILTERS
    // ============================================================
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

    // ============================================================
    //  PREVIEW MODE
    // ============================================================
    private void openAppPreview(String appName) {
        checkDayRollover();
        if (focusMode) {
            showWarning("Focus Mode Active", "Focus Mode is ON. Turn it OFF to use apps.");
            return;
        }
        if (studyRunning) {
            showWarning("Study Session Active", "Stop your study session first.");
            return;
        }

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

        screenTimer = new TimerThread("screen-timer", new TimerThread.TickListener() {
            @Override public void onTick(int s) {
                screenSeconds = s;
                previewTimerLabel.setText(formatTime(s));
                screenProgress.setProgress(Math.min((double) s / 3600, 1.0));
            }
            @Override public void onFinish(int s) {}
        });
        screenTimer.start();
    }

    private void applyPreviewTheme(String appName) {
        String accent, content;
        switch (appName) {
            case "YouTube":   accent = "#FF0000"; content = "YouTube\n\nTrending videos, subscriptions, recommended content\n\n(Simulated session)"; break;
            case "Facebook":  accent = "#1877F2"; content = "Facebook\n\nNews feed, friends, groups\n\n(Simulated session)"; break;
            case "Instagram": accent = "#E1306C"; content = "Instagram\n\nStories, reels, explore\n\n(Simulated session)"; break;
            case "WhatsApp":  accent = "#25D366"; content = "WhatsApp\n\nFamily group, chats\n\n(Simulated session)"; break;
            case "Chrome":    accent = "#4285F4"; content = "Chrome\n\nOpen tabs, search results\n\n(Simulated session)"; break;
            default:          accent = "#1769ff"; content = "(Simulated session)";
        }
        previewHeader.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 12 12 0 0; -fx-padding: 20;");
        previewAppNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        previewTimerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        previewBackBtn.setStyle("-fx-background-color: rgba(255,255,255,0.25); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 16;");
        previewContentTitle.setText(content);
    }

    private void endPreview() {
        if (screenTimer != null) { screenTimer.stopTimer(); screenTimer = null; }

        if (screenSeconds >= 3) {
            addOrMergeEntry(currentApp, "Screen Time", screenSeconds);
            sessionsToday++;
            threadManager.getQueue().put(new ActivityEntry(currentApp, "Screen Time", screenSeconds));

            // FIX: record progress so goals/score update
            recordProgress("Screen Time", screenSeconds);

            showAlert("Session Ended", "Time spent on " + currentApp + ": " + formatTime(screenSeconds));
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

    // ============================================================
    //  STUDY SESSION
    // ============================================================
    @FXML private void toggleStudySession() {
        if (studyRunning) {
            studyRunning = false;
            if (studyTimer != null) { studyTimer.stopTimer(); studyTimer = null; }
            studyToggleButton.setText("Start Study Session");

            if (studySeconds >= 3) {
                String subject = studySubjectField.getText().isEmpty() ? "General Study" : studySubjectField.getText();
                addOrMergeEntry("[S] " + subject, "Study Time", studySeconds);
                totalStudySeconds += studySeconds;
                sessionsToday++;
                threadManager.getQueue().put(new ActivityEntry("[S] " + subject, "Study Time", studySeconds));

                // FIX: record progress so goals/score update
                recordProgress("Study Time", studySeconds);

                updateStudyStats();
            }
            studySeconds = 0;
            studyTimerLabel.setText("00:00:00");
        } else {
            checkDayRollover();
            if (inPreviewMode) {
                showWarning("Screen Session Active", "Stop the app session first.");
                return;
            }
            studyRunning = true;
            studySeconds = 0;
            studyToggleButton.setText("Stop Session");
            studyTimer = new TimerThread("study-timer", new TimerThread.TickListener() {
                @Override public void onTick(int s) {
                    studySeconds = s;
                    studyTimerLabel.setText(formatTime(s));
                }
                @Override public void onFinish(int s) {}
            });
            studyTimer.start();
        }
    }

    private void updateStudyStats() {
        studyTotalLabel.setText(String.format("%dh %02dm",
                totalStudySeconds / 3600, (totalStudySeconds % 3600) / 60));
        studySessionsLabel.setText(String.valueOf(sessionsToday));
    }

    // ============================================================
    //  SLEEP
    // ============================================================
    @FXML private void calculateSleep() {
        try {
            LocalTime bed = LocalTime.of(Integer.parseInt(bedHourCombo.getValue()), Integer.parseInt(bedMinuteCombo.getValue()));
            LocalTime wake = LocalTime.of(Integer.parseInt(wakeHourCombo.getValue()), Integer.parseInt(wakeMinuteCombo.getValue()));
            SleepLog log = new SleepLog(LocalDate.now(), bed, wake);
            long h = log.getHoursSlept();
            String suffix = h < 5 ? "  (Too little!)" : (h > 9 ? "  (Oversleeping?)" : "  (Healthy!)");
            sleepResultLabel.setText(log.getFormattedDuration() + suffix);

            int sleepSeconds = (int)(log.getTotalMinutes() * 60);

            // Remove ALL existing sleep rows (memory + DB)
            activityLog.removeIf(e -> "Sleep Time".equalsIgnoreCase(e.getCategory()));
            try { activityDAO.deleteByCategory("Sleep Time"); }
            catch (SQLException ex) { System.err.println("Sleep delete failed: " + ex.getMessage()); }

            // Reset sleep goals
            for (Goal g : goals) {
                if ("Sleep Time".equals(g.getCategory())) {
                    g.resetProgress();
                    try { goalDAO.updateProgress(g); } catch (SQLException ignored) {}
                }
            }

            if (sleepSeconds > 0) {
                ActivityEntry sleepEntry = new ActivityEntry("[Z] Sleep", "Sleep Time", sleepSeconds);
                activityLog.add(sleepEntry);
                try { activityDAO.insert(sleepEntry); } catch (SQLException ignored) {}
                recordProgress("Sleep Time", sleepSeconds);
            }

            activityTable.refresh();
            updateScore(); updateGoalPanel(); updatePieChart(); updateBottomBar();
            refreshAchievements();
        } catch (Exception ex) {
            sleepResultLabel.setText("Invalid input");
        }
    }

    // ============================================================
    //  MERGE (memory + DB)
    // ============================================================
    private void addOrMergeEntry(String name, String category, int seconds) {
        if (seconds <= 0) return;
        for (ActivityEntry e : activityLog) {
            if (e.getName().equalsIgnoreCase(name) && e.getCategory().equalsIgnoreCase(category)) {
                e.addDuration(seconds);
                activityTable.refresh();
                try { activityDAO.mergeOrInsert(new ActivityEntry(name, category, seconds)); }
                catch (SQLException ex) { System.err.println("DB merge failed: " + ex.getMessage()); }
                return;
            }
        }
        ActivityEntry entry = new ActivityEntry(name, category, seconds);
        activityLog.add(entry);
        activityTable.refresh();
        try { activityDAO.mergeOrInsert(entry); }
        catch (SQLException ex) { System.err.println("DB insert failed: " + ex.getMessage()); }
    }

    // ============================================================
    //  GOAL PROGRESS + ACHIEVEMENTS + SCORE
    // ============================================================
    private void recordProgress(String category, int seconds) {
        if (seconds <= 0) return;

        for (Goal g : goals) {
            if (g.getCategory().equals(category)) {
                g.addProgress(seconds);
                try { goalDAO.updateProgress(g); }
                catch (SQLException ex) { System.err.println("Goal update failed: " + ex.getMessage()); }
            }
        }

        checkNewAchievements();
        refreshAchievements();
        updateScore();
        updateGoalPanel();
        updatePieChart();
        updateBottomBar();
    }

    private void checkNewAchievements() {
        Set<String> nowCompleted = new HashSet<>();
        for (Goal g : goals) if (g.isCompleted()) nowCompleted.add(g.getName());

        for (String name : nowCompleted) {
            if (!previouslyCompletedGoals.contains(name)) {
                showToast("Achievement Unlocked: " + name);
            }
        }
        previouslyCompletedGoals = nowCompleted;
    }

    private void refreshAchievements() {
        achievements.clear();
        for (Goal g : goals) if (g.isCompleted()) achievements.add("[Trophy] " + g.getName());
        if (achieveLabel != null) achieveLabel.setText(String.valueOf(achievements.size()));
    }

    private void updateScore() {
        int studyGoalSec = 0;
        for (Goal g : goals) if ("Study Time".equals(g.getCategory())) studyGoalSec += g.getTargetSeconds();

        if (studyGoalSec <= 0) {
            scoreNumber.setText("0");
            scoreStatus.setText("No study goal");
            scoreMessage.setText("Set a Study Goal");
            return;
        }

        int studySec = 0, productiveSec = 0, nonProdSec = 0;
        for (ActivityEntry e : activityLog) {
            if ("Study Time".equals(e.getCategory())) {
                studySec += e.getDurationSeconds();
            } else if ("Screen Time".equals(e.getCategory())) {
                String clean = e.getName().replace("[S] ", "").replace("[Z] ", "").trim();
                if (productiveApps.contains(clean)) productiveSec += e.getDurationSeconds();
                else nonProdSec += e.getDurationSeconds();
            }
        }

        double net = studySec + (productiveSec * 0.5) - nonProdSec;
        double raw = (net / studyGoalSec) * 100;
        int score = (int) Math.max(0, Math.min(100, Math.round(raw)));

        scoreNumber.setText(String.valueOf(score));
        if (score >= 80) scoreStatus.setText("Excellent Day!");
        else if (score >= 50) scoreStatus.setText("Good Day!");
        else if (score >= 20) scoreStatus.setText("Keep Going");
        else scoreStatus.setText("Just Starting");

        scoreMessage.setText(formatShort(studySec) + " study - " + formatShort(nonProdSec) + " non-prod");

        // Apply score-based color to circle (fix #9)
        if (scoreNumber.getParent() != null) {
            scoreNumber.getParent().getStyleClass().removeAll("score-low", "score-mid", "score-high");
            if (score >= 80) scoreNumber.getParent().getStyleClass().add("score-high");
            else if (score >= 50) scoreNumber.getParent().getStyleClass().add("score-mid");
            else scoreNumber.getParent().getStyleClass().add("score-low");
        }
    }

    private String formatShort(int s) {
        int m = s / 60;
        if (m >= 60) return (m / 60) + "h " + (m % 60) + "m";
        return m + "m";
    }

    private void updateGoalPanel() {
        int studyT = 0, studyC = 0, screenT = 0, screenC = 0, sleepT = 0, sleepC = 0;
        for (Goal g : goals) {
            switch (g.getCategory()) {
                case "Study Time":  studyT += g.getTargetSeconds();  studyC += g.getCurrentSeconds();  break;
                case "Screen Time": screenT += g.getTargetSeconds(); screenC += g.getCurrentSeconds(); break;
                case "Sleep Time":  sleepT += g.getTargetSeconds();  sleepC += g.getCurrentSeconds();  break;
            }
        }
        applyGoalUi(goalStudyBar, goalStudyValue, studyC, studyT);
        applyGoalUi(goalScreenBar, goalScreenValue, screenC, screenT);
        applyGoalUi(goalSleepBar, goalSleepValue, sleepC, sleepT);
    }

    private void applyGoalUi(ProgressBar bar, Label value, int current, int target) {
        if (target > 0) {
            bar.setProgress(Math.min(1.0, (double) current / target));
            value.setText(formatShort(current) + " / " + formatShort(target));
        } else {
            bar.setProgress(0);
            value.setText("No goal");
        }
    }

    private void updatePieChart() {
        int screen = 0, study = 0, sleep = 0;
        for (ActivityEntry e : activityLog) {
            String cat = e.getCategory();
            if ("Screen Time".equals(cat)) screen += e.getDurationSeconds();
            else if ("Study Time".equals(cat)) study += e.getDurationSeconds();
            else if ("Sleep Time".equals(cat)) sleep += e.getDurationSeconds();
        }

        activityPieChart.getData().clear();
        int total = screen + study + sleep;

        if (total == 0) {
            activityPieChart.getData().add(new PieChart.Data("No activity yet", 1));
            return;
        }

        if (screen > 0) activityPieChart.getData().add(
                new PieChart.Data(String.format("Screen %.0f%%", screen * 100.0 / total), screen));
        if (study > 0) activityPieChart.getData().add(
                new PieChart.Data(String.format("Study %.0f%%", study * 100.0 / total), study));
        if (sleep > 0) activityPieChart.getData().add(
                new PieChart.Data(String.format("Sleep %.0f%%", sleep * 100.0 / total), sleep));
    }

    private void updateBottomBar() {
        if (achieveLabel != null) achieveLabel.setText(String.valueOf(achievements.size()));
        if (sessionsLabel != null) sessionsLabel.setText(String.valueOf(sessionsToday));
        int active = activityLog.isEmpty() ? 0 : 1;
        if (streakLabel != null) streakLabel.setText(String.valueOf(active));

        if (balanceLabel != null) {
            int score = 0;
            try { score = Integer.parseInt(scoreNumber.getText()); } catch (Exception ignored) {}
            if (score >= 80) { balanceLabel.setText("Great Balance!"); balanceSubLabel.setText("You're doing awesome!"); }
            else if (score >= 50) { balanceLabel.setText("On Track"); balanceSubLabel.setText("Keep it up!"); }
            else if (activityLog.isEmpty()) { balanceLabel.setText("Get Started"); balanceSubLabel.setText("Do an activity"); }
            else { balanceLabel.setText("Needs Focus"); balanceSubLabel.setText("Try less screen time"); }
        }
    }

    // ============================================================
    //  FOCUS MODE / DARK MODE
    // ============================================================
    @FXML private void toggleFocusMode() {
        focusMode = !focusMode;
        if (focusMode) {
            focusModeButton.setText("Focus Mode: ON");
            focusModeButton.setStyle("-fx-background-color: #e8f0ff; -fx-text-fill: #0758ed; -fx-font-weight: bold;");
            if (inPreviewMode) endPreview();
        } else {
            focusModeButton.setText("Focus Mode");
            focusModeButton.setStyle("");
        }
    }

    @FXML private void toggleDarkMode() {
        darkMode = !darkMode;
        Scene scene = screenTimeStack.getScene();
        if (darkMode) {
            if (!scene.getRoot().getStyleClass().contains("dark-mode"))
                scene.getRoot().getStyleClass().add("dark-mode");
            darkModeBtn.setText("Light Mode");
        } else {
            scene.getRoot().getStyleClass().remove("dark-mode");
            darkModeBtn.setText("Dark Mode");
        }
        updatePieChart();
    }

    // ============================================================
    //  SIDEBAR
    // ============================================================
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
                Goal g = new Goal(n, t, catField.getValue());
                goals.add(g);
                goalDAO.insert(g);
                nameField.clear();
                targetField.clear();
                goalList.getChildren().clear();
                goalList.getChildren().add(listTitle);
                renderGoalList(goalList);
                status.setText("Goal added");
                status.setStyle("-fx-text-fill: green;");
                refreshAchievements();
                updateScore();
                updateGoalPanel();
                updateBottomBar();
            } catch (Exception ex) {
                status.setText("Enter valid values.");
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
            String marker = g.isCompleted() ? "  [Done]" : "";
            Label info = new Label(g.getName() + " (" + g.getCategory() + ")\n" + g.getProgressText() + marker);
            info.setStyle("-fx-font-size: 13px;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button del = new Button("Delete");
            del.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4;");
            del.setOnAction(e -> {
                goals.remove(g);
                try { goalDAO.delete(g); } catch (SQLException ex) { System.err.println(ex.getMessage()); }
                container.getChildren().clear();
                Label lt = new Label("Current Goals:");
                lt.setStyle("-fx-font-weight: bold;");
                container.getChildren().add(lt);
                renderGoalList(container);
                refreshAchievements();
                updateScore();
                updateGoalPanel();
                updateBottomBar();
            });
            row.getChildren().addAll(info, spacer, del);
            container.getChildren().add(row);
        }
    }

    @FXML private void openHistoryWindow() {
        StringBuilder sb = new StringBuilder();
        if (activityLog.isEmpty()) sb.append("No activity recorded yet.");
        else for (ActivityEntry e : activityLog)
            sb.append("- ").append(e.getName()).append(" - ").append(e.getDurationFormatted())
                    .append(" (").append(e.getCategory()).append(")\n");
        openInfoWindow("History", "Today's Session History", sb.toString());
    }

    @FXML private void openAchievementsWindow() {
        StringBuilder sb = new StringBuilder();
        if (achievements.isEmpty()) sb.append("No achievements yet.\nComplete a goal to unlock one!");
        else for (String a : achievements) sb.append(a).append("\n");
        openInfoWindow("Achievements", "Your Achievements", sb.toString());
    }

    @FXML private void openReportsWindow() {
        try {
            List<ActivityEntry> snapshot = new ArrayList<>(activityLog);
            Future<StatsCalculator.Stats> future = threadManager.submitCalculation(new StatsCalculator(snapshot));
            long completed = goals.stream().filter(Goal::isCompleted).count();
            StatsCalculator.Stats stats = future.get();
            openInfoWindow("Reports", "Today's Report",
                    stats.toString() + "\n" +
                            "Goals Completed: " + completed + " / " + goals.size() + "\n" +
                            "Productivity Score: " + scoreNumber.getText() + "%");
        } catch (Exception ex) {
            openInfoWindow("Reports", "Error", "Could not compute stats: " + ex.getMessage());
        }
    }

    @FXML private void openThreadMonitor() {
        if (threadMonitorStage != null && threadMonitorStage.isShowing()) {
            threadMonitorStage.setIconified(false);
            threadMonitorStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/ThreadMonitor.fxml"));
            Parent root = loader.load();
            ThreadMonitorController controller = loader.getController();
            threadMonitorStage = new Stage();
            threadMonitorStage.setTitle("Thread Monitor - Week 4");
            threadMonitorStage.initModality(Modality.NONE);
            threadMonitorStage.setScene(new Scene(root, 780, 620));
            threadMonitorStage.setOnCloseRequest(e -> {
                controller.stop();
                threadMonitorStage = null;
            });
            threadMonitorStage.show();
        } catch (Exception ex) { ex.printStackTrace(); }
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

        Label appCatTitle = new Label("App Categorization");
        appCatTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        Label appCatHint = new Label("Productive apps help score. Non-Productive apps hurt score.");
        appCatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #68738a;");

        VBox appList = new VBox(6);
        for (String app : Arrays.asList("YouTube", "Facebook", "Instagram", "WhatsApp", "Chrome")) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label(app);
            lbl.setMinWidth(120);
            ToggleButton toggle = new ToggleButton();
            boolean isProd = productiveApps.contains(app);
            toggle.setSelected(isProd);
            toggle.setText(isProd ? "Productive" : "Non-Productive");
            toggle.setStyle(isProd
                    ? "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-background-radius: 6; -fx-font-weight: bold;"
                    : "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 6; -fx-font-weight: bold;");
            toggle.setOnAction(e -> {
                boolean nowProd = toggle.isSelected();
                if (nowProd) {
                    productiveApps.add(app);
                    nonProductiveApps.remove(app);
                    toggle.setText("Productive");
                    toggle.setStyle("-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-background-radius: 6; -fx-font-weight: bold;");
                } else {
                    productiveApps.remove(app);
                    nonProductiveApps.add(app);
                    toggle.setText("Non-Productive");
                    toggle.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 6; -fx-font-weight: bold;");
                }
                try { categorizationDAO.save(app, nowProd); }
                catch (SQLException ex) { System.err.println("Save failed: " + ex.getMessage()); }
                updateScore();
                updateGoalPanel();
                updatePieChart();
                updateBottomBar();
                refreshAchievements();
            });
            row.getChildren().addAll(lbl, toggle);
            appList.getChildren().add(row);
        }

        Label prefTitle = new Label("Preferences");
        prefTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        CheckBox notifChk = new CheckBox("Enable notifications");
        notifChk.setSelected(true);
        CheckBox soundChk = new CheckBox("Enable sound alerts");

        Button manageGoalsBtn = new Button("Manage Goals...");
        manageGoalsBtn.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        manageGoalsBtn.setOnAction(e -> { stage.close(); openGoalsWindow(); });

        Button resetBtn = new Button("Reset Today's Data");
        resetBtn.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        resetBtn.setOnAction(e -> {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Reset today's activities?", ButtonType.OK, ButtonType.CANCEL);
            if (c.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                // Stop timers
                if (screenTimer != null) { screenTimer.stopTimer(); screenTimer = null; }
                if (studyTimer != null) { studyTimer.stopTimer(); studyTimer = null; }
                inPreviewMode = false;
                studyRunning = false;
                screenTimeContent.setVisible(true);
                screenTimeContent.setManaged(true);
                previewOverlay.setVisible(false);
                previewOverlay.setManaged(false);

                // Clear DB + memory
                try {
                    activityDAO.deleteToday();
                    goalDAO.deleteAll();
                } catch (SQLException ex) { System.err.println("Reset failed: " + ex.getMessage()); }

                activityLog.clear();
                achievements.clear();
                goals.clear();
                totalStudySeconds = 0;
                sessionsToday = 0;
                previouslyCompletedGoals.clear();
                threadManager.getCounter().reset();
                threadManager.getQueue().clear();
                updateStudyStats();
                refreshAchievements();
                updateScore();
                updateGoalPanel();
                updatePieChart();
                updateBottomBar();
            }
        });

        Label aboutTitle = new Label("About");
        aboutTitle.setStyle("-fx-font-weight: bold;");
        Label about = new Label("Personalized Activity and Screen Time Manager\nv1.0 - Lab Project (Weeks 1-6)");

        root.getChildren().addAll(title, appCatTitle, appCatHint, appList, new Separator(),
                prefTitle, notifChk, soundChk, new Separator(), manageGoalsBtn, resetBtn,
                new Separator(), aboutTitle, about);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        stage.setScene(new Scene(sp, 520, 720));
        stage.show();
    }

    // ============================================================
    //  HEADER
    // ============================================================
    @FXML private void showDate() {
        openInfoWindow("Today", "Current Date", "Today is " + LocalDate.now());
    }

    @FXML private void showNotifications() {
        StringBuilder sb = new StringBuilder();
        for (String n : notifications) sb.append("- ").append(n).append("\n\n");
        openInfoWindow("Notifications", "Notifications", sb.toString());
    }

    @FXML private void showProfile() {
        long completed = goals.stream().filter(Goal::isCompleted).count();
        openInfoWindow("Profile", "User Profile",
                "Name: Student\nRole: University Student\nTotal Goals: " + goals.size() +
                        "\nCompleted: " + completed + "\nAchievements: " + achievements.size());
    }

    // ============================================================
    //  RIGHT PANEL
    // ============================================================
    @FXML private void showPieInfo() {
        openInfoWindow("Chart Info", "Activity Breakdown",
                "This chart shows the proportional breakdown of your tracked time:\n" +
                        "- Screen Time\n- Study Time\n- Sleep Time\n\n" +
                        "Percentages are of TRACKED time (not the full 24-hour day).");
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

    // ============================================================
    //  LIFECYCLE
    // ============================================================
    public void stopAll() {
        if (screenTimer != null) screenTimer.stopTimer();
        if (studyTimer != null) studyTimer.stopTimer();
        if (dayCheckScheduler != null && !dayCheckScheduler.isShutdown()) {
            dayCheckScheduler.shutdownNow();
        }
    }

    // ============================================================
    //  HELPERS
    // ============================================================
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
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefHeight(320);
        area.setStyle("-fx-font-size: 14px;");
        root.getChildren().addAll(h, area);
        stage.setScene(new Scene(root, 520, 440));
        stage.show();
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showWarning(String title, String content) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    /** Non-blocking toast that auto-hides after 2.5 seconds. */
    private void showToast(String message) {
        try {
            Stage toast = new Stage();
            toast.initStyle(StageStyle.TRANSPARENT);
            if (screenTimeStack.getScene() != null && screenTimeStack.getScene().getWindow() != null) {
                toast.initOwner(screenTimeStack.getScene().getWindow());
            }
            toast.initModality(Modality.NONE);

            Label label = new Label(message);
            label.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-padding: 14 28; " +
                    "-fx-background-radius: 10; -fx-font-size: 15px; -fx-font-weight: bold;");

            StackPane root = new StackPane(label);
            root.setStyle("-fx-background-color: transparent;");
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            toast.setScene(scene);
            toast.show();

            if (screenTimeStack.getScene().getWindow() instanceof Stage owner) {
                toast.setX(owner.getX() + owner.getWidth() / 2 - 150);
                toast.setY(owner.getY() + owner.getHeight() - 120);
            }

            PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
            delay.setOnFinished(e -> toast.close());
            delay.play();
        } catch (Exception ignored) {}
    }
}