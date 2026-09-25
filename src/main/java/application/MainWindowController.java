package application;

import database.ActivityDAO;
import database.AppDAO;
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
import model.AppInfo;
import model.Goal;
import model.SleepLog;
import model.WellnessTip;
import service.StatsCalculator;
import service.ThreadManager;
import service.TimerThread;
import service.WellnessTipFetcher;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainWindowController {

    @FXML private TabPane mainTabPane;
    @FXML private ComboBox<String> appComboBox, filterComboBox;
    @FXML private TextField searchField;
    @FXML private Label timerLabel, tipLabel;
    @FXML private ProgressBar screenProgress;
    @FXML private TableView<ActivityEntry> activityTable;
    @FXML private TableColumn<ActivityEntry, String> nameColumn, categoryColumn, durationColumn;
    @FXML private PieChart activityPieChart;
    @FXML private FlowPane appIconBar;
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
            profileBtn, infoButton, prevTipBtn, nextTipBtn, editGoalsBtn,
            fetchTipBtn, manageAppsBtn;
    @FXML private Label scoreNumber, scoreStatus, scoreMessage;
    @FXML private ProgressBar goalStudyBar, goalScreenBar, goalSleepBar;
    @FXML private Label goalStudyValue, goalScreenValue, goalSleepValue;
    @FXML private Label streakLabel, achieveLabel, sessionsLabel, balanceLabel, balanceSubLabel;

    private final ActivityDAO activityDAO = new ActivityDAO();
    private final GoalDAO goalDAO = new GoalDAO();
    private final AppDAO appDAO = new AppDAO();
    private final WellnessTipFetcher tipFetcher = new WellnessTipFetcher();

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
    private final ObservableList<AppInfo> installedApps = FXCollections.observableArrayList();

    private final Set<String> productiveApps = new LinkedHashSet<>();
    private final Set<String> nonProductiveApps = new LinkedHashSet<>();

    private final List<String> tips = new ArrayList<>();
    private int currentTipIndex = 0;
    private final List<String> notifications = new ArrayList<>();

    private final ThreadManager threadManager = ThreadManager.getInstance();
    private LocalDate lastActiveDate = LocalDate.now();
    private Stage threadMonitorStage = null;

    private Set<String> previouslyCompletedGoals = new HashSet<>();

    private static final ScheduledExecutorService DAY_CHECK_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "day-rollover-check");
                t.setDaemon(true);
                return t;
            });
    private static volatile MainWindowController activeInstance = null;
    private static volatile boolean dayScheduled = false;

    @FXML
    public void initialize() {
        activeInstance = this;
        loadAppsFromDatabase();
        loadActivitiesAndGoals();
        setupScreenTab();
        setupStudyTab();
        setupSleepTab();
        setupTips();
        setupNotifications();

        threadManager.startQueueConsumer(() -> {
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

        Platform.runLater(this::fetchLiveTip);

        if (!dayScheduled) {
            dayScheduled = true;
            DAY_CHECK_SCHEDULER.scheduleAtFixedRate(() -> {
                MainWindowController inst = activeInstance;
                if (inst != null && !LocalDate.now().equals(inst.lastActiveDate)) {
                    Platform.runLater(inst::performDayRollover);
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
    }

    private void loadAppsFromDatabase() {
        try {
            List<AppInfo> apps = appDAO.getAllSortedByRecent();
            installedApps.setAll(apps);
            productiveApps.clear();
            nonProductiveApps.clear();
            for (AppInfo a : apps) {
                if (a.isProductive()) productiveApps.add(a.getName());
                else nonProductiveApps.add(a.getName());
            }
        } catch (SQLException ex) {
            System.err.println("Failed to load apps: " + ex.getMessage());
        }
    }

    private void loadActivitiesAndGoals() {
        try {
            activityLog.setAll(activityDAO.getTodayActivities());
            totalStudySeconds = 0;
            sessionsToday = 0;
            for (ActivityEntry e : activityLog) {
                if ("Study Time".equals(e.getCategory())) totalStudySeconds += e.getDurationSeconds();
                sessionsToday++;
            }
        } catch (SQLException ex) { System.err.println("Failed to load activities: " + ex.getMessage()); }

        try {
            goals.setAll(goalDAO.getAllGoals());
        } catch (SQLException ex) { System.err.println("Failed to load goals: " + ex.getMessage()); }

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

        appComboBox.setOnAction(e -> {
            String v = appComboBox.getValue();
            if (v != null) { openAppPreview(v); appComboBox.setValue(null); }
        });

        previewBackBtn.setOnAction(e -> endPreview());
        rebuildAppUi();
    }

    private void rebuildAppUi() {
        List<String> names = new ArrayList<>();
        for (AppInfo app : installedApps) names.add(app.getName());
        appComboBox.setItems(FXCollections.observableArrayList(names));

        if (appIconBar == null) return;
        appIconBar.getChildren().clear();

        if (installedApps.isEmpty()) {
            Button install = new Button("+ Install your first app");
            install.getStyleClass().add("install-app-button");
            install.setOnAction(e -> openManageAppsWindow());
            appIconBar.getChildren().add(install);
            return;
        }

        for (AppInfo app : installedApps) {
            appIconBar.getChildren().add(createAppIconButton(app));
        }

        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("add-app-button");
        addBtn.setTooltip(new Tooltip("Install a new app"));
        addBtn.setOnAction(e -> openManageAppsWindow());
        appIconBar.getChildren().add(addBtn);
    }

    private Button createAppIconButton(AppInfo app) {
        Button btn = new Button(app.getIcon() == null || app.getIcon().isEmpty()
                ? app.getName().substring(0, 1).toUpperCase()
                : app.getIcon());
        btn.getStyleClass().add("app-icon-button");
        btn.setTooltip(new Tooltip(app.getName() + "  (right-click to manage)"));
        btn.setOnAction(e -> openAppPreview(app.getName()));

        ContextMenu menu = new ContextMenu();
        MenuItem uninstall = new MenuItem("Uninstall " + app.getName());
        uninstall.setOnAction(e -> confirmUninstall(app));
        menu.getItems().add(uninstall);
        btn.setContextMenu(menu);

        return btn;
    }

    private void confirmUninstall(AppInfo app) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Uninstall App");
        c.setHeaderText("Uninstall " + app.getName() + "?");
        c.setContentText("This will delete the app and all its tracked activity history.");
        Optional<ButtonType> res = c.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        if (inPreviewMode && app.getName().equals(currentApp)) endPreview();

        try {
            appDAO.uninstall(app.getName());
            installedApps.removeIf(a -> a.getName().equals(app.getName()));
            productiveApps.remove(app.getName());
            nonProductiveApps.remove(app.getName());
            activityLog.removeIf(e -> e.getName().equalsIgnoreCase(app.getName())
                    && "Screen Time".equals(e.getCategory()));
            activityTable.refresh();
            rebuildAppUi();
            updateScore(); updateGoalPanel(); updatePieChart(); updateBottomBar(); refreshAchievements();
            showToast(app.getName() + " uninstalled");
        } catch (SQLException ex) {
            showWarning("Uninstall failed", ex.getMessage());
        }
    }

    private void setupStudyTab() { studyTimerLabel.setText("00:00:00"); updateStudyStats(); }

    private void setupSleepTab() {
        for (int i = 0; i < 24; i++) {
            String h = String.format("%02d", i);
            bedHourCombo.getItems().add(h); wakeHourCombo.getItems().add(h);
        }
        for (int i = 0; i < 60; i++) {
            String m = String.format("%02d", i);
            bedMinuteCombo.getItems().add(m); wakeMinuteCombo.getItems().add(m);
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
        tipLabel.setText("Fetching a live wellness tip...");
    }

    private void setupNotifications() {
        notifications.add("Welcome! Set a goal to see your productivity score.");
        notifications.add("Tip: Click an app icon to start tracking.");
    }

    @FXML private void fetchLiveTip() {
        if (tipLabel == null) return;
        tipLabel.setText("Fetching live tip...");
        if (fetchTipBtn != null) fetchTipBtn.setDisable(true);

        threadManager.getCalcPool().submit(() -> {
            try {
                WellnessTip tip = tipFetcher.fetchRandom();
                Platform.runLater(() -> {
                    tipLabel.setText(tip.toString());
                    if (fetchTipBtn != null) fetchTipBtn.setDisable(false);
                });
            } catch (Exception ex) {
                System.err.println("[WellnessTip] fetch failed: " + ex.getMessage());
                final String reason = ex.getMessage() != null ? ex.getMessage() : "network error";
                Platform.runLater(() -> {
                    tipLabel.setText("Offline tip: " + tips.get(currentTipIndex) + "   [" + reason + "]");
                    if (fetchTipBtn != null) fetchTipBtn.setDisable(false);
                });
            }
        });
    }

    private void checkDayRollover() {
        if (!LocalDate.now().equals(lastActiveDate)) performDayRollover();
    }

    private void performDayRollover() {
        lastActiveDate = LocalDate.now();
        for (Goal g : goals) {
            g.resetProgress();
            try { goalDAO.updateProgress(g); } catch (SQLException ignored) {}
        }
        try { goals.setAll(goalDAO.getAllGoals()); } catch (SQLException ignored) {}
        try { activityLog.setAll(activityDAO.getTodayActivities()); }
        catch (SQLException ex) { activityLog.clear(); }

        totalStudySeconds = 0;
        sessionsToday = 0;
        previouslyCompletedGoals.clear();
        threadManager.getCounter().reset();
        threadManager.getQueue().clear();

        refreshAchievements(); updateScore(); updateGoalPanel(); updatePieChart(); updateBottomBar();
    }

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

    private void openAppPreview(String appName) {
        checkDayRollover();
        if (focusMode) { showWarning("Focus Mode Active", "Focus Mode is ON. Turn it OFF to use apps."); return; }
        if (studyRunning) { showWarning("Study Session Active", "Stop your study session first."); return; }

        try {
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
        } catch (Exception ex) {
            inPreviewMode = false;
            screenTimeContent.setVisible(true);
            screenTimeContent.setManaged(true);
            previewOverlay.setVisible(false);
            previewOverlay.setManaged(false);
            showWarning("Failed to open preview", ex.getMessage());
        }
    }

    private void applyPreviewTheme(String appName) {
        String accent = colorFor(appName);
        String icon = iconFor(appName);
        String content = icon + "  " + appName + "\n\n(Simulated session)\n\nTap 'Back to Home' to end the session and save.";

        previewHeader.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 12 12 0 0; -fx-padding: 20;");
        previewAppNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        previewTimerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        previewBackBtn.setStyle("-fx-background-color: rgba(255,255,255,0.25); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 16;");
        previewContentTitle.setText(content);
    }

    private String colorFor(String appName) {
        int hash = Math.abs(appName.hashCode());
        String[] palette = {"#FF0000", "#1877F2", "#E1306C", "#25D366",
                "#4285F4", "#8052d2", "#f59e0b", "#18a957", "#0e7490"};
        return palette[hash % palette.length];
    }

    private String iconFor(String appName) {
        for (AppInfo a : installedApps) if (a.getName().equals(appName)) return a.getIcon();
        return "▶";
    }

    private void endPreview() {
        if (screenTimer != null) { screenTimer.stopTimer(); screenTimer = null; }

        if (screenSeconds >= 3) {
            addOrMergeEntry(currentApp, "Screen Time", screenSeconds);
            sessionsToday++;
            threadManager.getQueue().put(new ActivityEntry(currentApp, "Screen Time", screenSeconds));
            recordProgress("Screen Time", screenSeconds);
            try { appDAO.markUsed(currentApp); } catch (SQLException ignored) {}
            loadAppsFromDatabase();
            rebuildAppUi();
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
                recordProgress("Study Time", studySeconds);
                updateStudyStats();
            }
            studySeconds = 0;
            studyTimerLabel.setText("00:00:00");
        } else {
            checkDayRollover();
            if (inPreviewMode) { showWarning("Screen Session Active", "Stop the app session first."); return; }
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
        studyTotalLabel.setText(String.format("%dh %02dm", totalStudySeconds / 3600, (totalStudySeconds % 3600) / 60));
        studySessionsLabel.setText(String.valueOf(sessionsToday));
    }

    @FXML private void calculateSleep() {
        try {
            LocalTime bed = LocalTime.of(Integer.parseInt(bedHourCombo.getValue()), Integer.parseInt(bedMinuteCombo.getValue()));
            LocalTime wake = LocalTime.of(Integer.parseInt(wakeHourCombo.getValue()), Integer.parseInt(wakeMinuteCombo.getValue()));
            SleepLog log = new SleepLog(LocalDate.now(), bed, wake);
            long h = log.getHoursSlept();

            String suffix;
            if (h == 0) suffix = "  (Invalid: bed time equals wake time)";
            else if (h < 5) suffix = "  (Too little!)";
            else if (h > 9) suffix = "  (Oversleeping?)";
            else suffix = "  (Healthy!)";
            sleepResultLabel.setText(log.getFormattedDuration() + suffix);

            int sleepSeconds = (int)(log.getTotalMinutes() * 60);

            activityLog.removeIf(e -> "Sleep Time".equalsIgnoreCase(e.getCategory()));
            try { activityDAO.deleteByCategory("Sleep Time"); }
            catch (SQLException ex) { System.err.println("Sleep delete failed: " + ex.getMessage()); }

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
            if (!previouslyCompletedGoals.contains(name)) showToast("Achievement Unlocked: " + name);
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
                if (productiveApps.contains(e.getName())) productiveSec += e.getDurationSeconds();
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
        if (screen > 0) activityPieChart.getData().add(new PieChart.Data(String.format("Screen %.0f%%", screen * 100.0 / total), screen));
        if (study > 0) activityPieChart.getData().add(new PieChart.Data(String.format("Study %.0f%%", study * 100.0 / total), study));
        if (sleep > 0) activityPieChart.getData().add(new PieChart.Data(String.format("Sleep %.0f%%", sleep * 100.0 / total), sleep));
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

    @FXML private void openManageAppsWindow() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Manage Apps");

        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f7f9fd;");

        Label title = new Label("Installed Apps");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1769ff;");

        VBox list = new VBox(8);
        rebuildManageList(list);

        Separator sep = new Separator();
        Label addTitle = new Label("Install a New App");
        addTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");

        TextField nameField = new TextField();
        nameField.setPromptText("App name (e.g. Twitter)");

        TextField iconField = new TextField();
        iconField.setPromptText("Icon (emoji or 1-2 letters)");
        iconField.setPrefWidth(200);

        HBox emojiRow = new HBox(4);
        String[] presets = {"🎬", "📷", "💬", "🌐", "🎮", "📚", "🎵", "🛒", "☁", "⚙", "▶", "●"};
        for (String e : presets) {
            Button b = new Button(e);
            b.getStyleClass().add("emoji-preset-button");
            b.setOnAction(ev -> iconField.setText(e));
            emojiRow.getChildren().add(b);
        }

        ToggleGroup tg = new ToggleGroup();
        RadioButton prod = new RadioButton("Productive");
        prod.setToggleGroup(tg);
        RadioButton nonProd = new RadioButton("Non-Productive");
        nonProd.setToggleGroup(tg);
        nonProd.setSelected(true);
        HBox radioRow = new HBox(15, prod, nonProd);

        Button installBtn = new Button("+ Install");
        installBtn.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        Label status = new Label("");

        installBtn.setOnAction(e -> {
            try {
                String n = nameField.getText().trim();
                String ic = iconField.getText().trim();
                if (n.isEmpty()) {
                    status.setText("Please enter an app name.");
                    status.setStyle("-fx-text-fill: red;");
                    return;
                }
                if (ic.isEmpty()) ic = n.substring(0, 1).toUpperCase();

                if (appDAO.exists(n)) {
                    status.setText("An app named '" + n + "' already exists.");
                    status.setStyle("-fx-text-fill: red;");
                    return;
                }

                AppInfo a = new AppInfo(n, ic, prod.isSelected(), null, null);
                appDAO.insert(a);
                loadAppsFromDatabase();
                rebuildAppUi();
                rebuildManageList(list);
                nameField.clear(); iconField.clear(); nonProd.setSelected(true);
                status.setText("Installed " + n);
                status.setStyle("-fx-text-fill: green;");
                showToast(n + " installed");
            } catch (Exception ex) {
                status.setText("Failed: " + ex.getMessage());
                status.setStyle("-fx-text-fill: red;");
            }
        });

        root.getChildren().addAll(title, list, sep, addTitle,
                new Label("Name:"), nameField,
                new Label("Icon:"), iconField, emojiRow,
                radioRow, installBtn, status);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        stage.setScene(new Scene(sp, 560, 680));
        stage.show();
    }

    private void rebuildManageList(VBox list) {
        list.getChildren().clear();
        if (installedApps.isEmpty()) {
            Label empty = new Label("No apps installed.");
            empty.setStyle("-fx-text-fill: #68738a;");
            list.getChildren().add(empty);
            return;
        }
        for (AppInfo app : installedApps) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 6; -fx-border-color: #dce4ef; -fx-border-radius: 6;");

            Label icon = new Label(app.getIcon());
            icon.setStyle("-fx-font-size: 22px; -fx-min-width: 32;");

            VBox info = new VBox(2);
            Label nameLbl = new Label(app.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            String stat = app.isProductive() ? "Productive" : "Non-Productive";
            String used = app.getLastUsedDate() == null ? "Never used" : "Last used " + app.getLastUsedDate();
            Label subLbl = new Label(stat + "  |  " + used);
            subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #68738a;");
            info.getChildren().addAll(nameLbl, subLbl);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            ToggleButton prodToggle = new ToggleButton(app.isProductive() ? "Productive" : "Non-Productive");
            prodToggle.setSelected(app.isProductive());
            prodToggle.setStyle(app.isProductive()
                    ? "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-background-radius: 6; -fx-font-size: 11px;"
                    : "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 6; -fx-font-size: 11px;");
            prodToggle.setOnAction(e -> {
                boolean np = prodToggle.isSelected();
                try {
                    appDAO.updateProductivity(app.getName(), np);
                    app.setProductive(np);
                    prodToggle.setText(np ? "Productive" : "Non-Productive");
                    prodToggle.setStyle(np
                            ? "-fx-background-color: #d1fae5; -fx-text-fill: #065f46; -fx-background-radius: 6; -fx-font-size: 11px;"
                            : "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 6; -fx-font-size: 11px;");
                    loadAppsFromDatabase();
                    updateScore(); updateGoalPanel(); updatePieChart(); updateBottomBar();
                } catch (SQLException ex) { System.err.println(ex.getMessage()); }
            });

            Button del = new Button("Uninstall");
            del.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4;");
            del.setOnAction(e -> {
                confirmUninstall(app);
                rebuildManageList(list);
            });

            row.getChildren().addAll(icon, info, spacer, prodToggle, del);
            list.getChildren().add(row);
        }
    }

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
        TextField nameField = new TextField(); nameField.setPromptText("Goal name");
        TextField targetField = new TextField(); targetField.setPromptText("Target minutes");
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
                nameField.clear(); targetField.clear();
                goalList.getChildren().clear();
                goalList.getChildren().add(listTitle);
                renderGoalList(goalList);
                status.setText("Goal added");
                status.setStyle("-fx-text-fill: green;");
                refreshAchievements(); updateScore(); updateGoalPanel(); updateBottomBar();
            } catch (Exception ex) {
                status.setText("Enter valid values.");
                status.setStyle("-fx-text-fill: red;");
            }
        });

        root.getChildren().addAll(title, goalList, sep, addTitle, nameField, targetField, catField, addBtn, status);
        ScrollPane sp = new ScrollPane(root); sp.setFitToWidth(true);
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
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Button del = new Button("Delete");
            del.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 4;");
            del.setOnAction(e -> {
                goals.remove(g);
                try { goalDAO.delete(g); } catch (SQLException ex) { System.err.println(ex.getMessage()); }
                container.getChildren().clear();
                Label lt = new Label("Current Goals:"); lt.setStyle("-fx-font-weight: bold;");
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

        Label appsTitle = new Label("Apps");
        appsTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        Button manageAppsBtn2 = new Button("Manage Installed Apps...");
        manageAppsBtn2.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        manageAppsBtn2.setOnAction(e -> { stage.close(); openManageAppsWindow(); });

        Label prefTitle = new Label("Preferences");
        prefTitle.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        CheckBox notifChk = new CheckBox("Enable notifications"); notifChk.setSelected(true);
        CheckBox soundChk = new CheckBox("Enable sound alerts");

        Button manageGoalsBtn = new Button("Manage Goals...");
        manageGoalsBtn.setStyle("-fx-background-color: #1769ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        manageGoalsBtn.setOnAction(e -> { stage.close(); openGoalsWindow(); });

        Button resetBtn = new Button("Reset Today's Data");
        resetBtn.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6;");
        resetBtn.setOnAction(e -> {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Reset today's activities?", ButtonType.OK, ButtonType.CANCEL);
            if (c.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                if (screenTimer != null) { screenTimer.stopTimer(); screenTimer = null; }
                if (studyTimer != null) { studyTimer.stopTimer(); studyTimer = null; }
                inPreviewMode = false;
                studyRunning = false;
                screenTimeContent.setVisible(true);
                screenTimeContent.setManaged(true);
                previewOverlay.setVisible(false);
                previewOverlay.setManaged(false);

                try { activityDAO.deleteToday(); goalDAO.deleteAll(); }
                catch (SQLException ex) { System.err.println("Reset failed: " + ex.getMessage()); }

                activityLog.clear(); achievements.clear(); goals.clear();
                totalStudySeconds = 0; sessionsToday = 0;
                previouslyCompletedGoals.clear();
                threadManager.getCounter().reset();
                threadManager.getQueue().clear();
                if (searchField != null) searchField.clear();
                if (filterComboBox != null) filterComboBox.setValue("All");
                applyFilters();
                updateStudyStats(); refreshAchievements(); updateScore();
                updateGoalPanel(); updatePieChart(); updateBottomBar();
            }
        });

        Label aboutTitle = new Label("About"); aboutTitle.setStyle("-fx-font-weight: bold;");
        Label about = new Label("Personalized Activity and Screen Time Manager\nv1.0 - Lab Project (Weeks 1-7)");

        root.getChildren().addAll(title, appsTitle, manageAppsBtn2,
                new Separator(), prefTitle, notifChk, soundChk,
                new Separator(), manageGoalsBtn, resetBtn,
                new Separator(), aboutTitle, about);

        ScrollPane sp = new ScrollPane(root); sp.setFitToWidth(true);
        stage.setScene(new Scene(sp, 520, 720));
        stage.show();
    }

    @FXML private void showDate() { openInfoWindow("Today", "Current Date", "Today is " + LocalDate.now()); }

    @FXML private void showNotifications() {
        StringBuilder sb = new StringBuilder();
        for (String n : notifications) sb.append("- ").append(n).append("\n\n");
        openInfoWindow("Notifications", "Notifications", sb.toString());
    }

    @FXML private void showProfile() {
        long completed = goals.stream().filter(Goal::isCompleted).count();
        openInfoWindow("Profile", "User Profile",
                "Name: Student\nRole: University Student\nTotal Goals: " + goals.size() +
                        "\nCompleted: " + completed + "\nInstalled Apps: " + installedApps.size() +
                        "\nAchievements: " + achievements.size());
    }

    @FXML private void showPieInfo() {
        openInfoWindow("Chart Info", "Activity Breakdown",
                "This chart shows the proportional breakdown of your tracked time:\n" +
                        "- Screen Time\n- Study Time\n- Sleep Time\n\n" +
                        "Percentages are of TRACKED time.");
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

    public void stopAll() {
        if (screenTimer != null) screenTimer.stopTimer();
        if (studyTimer != null) studyTimer.stopTimer();
    }

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

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }

    private void showWarning(String title, String content) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content); a.showAndWait();
    }

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