package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String URL = "jdbc:sqlite:lifesync.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        String activities = "CREATE TABLE IF NOT EXISTS activities (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "duration_seconds INTEGER NOT NULL, " +
                "date TEXT NOT NULL)";

        String goals = "CREATE TABLE IF NOT EXISTS goals (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "target_seconds INTEGER NOT NULL, " +
                "category TEXT NOT NULL, " +
                "current_seconds INTEGER NOT NULL DEFAULT 0, " +
                "completed INTEGER NOT NULL DEFAULT 0, " +
                "date TEXT NOT NULL)";

        String apps = "CREATE TABLE IF NOT EXISTS apps (" +
                "name TEXT PRIMARY KEY, " +
                "icon TEXT NOT NULL, " +
                "productive INTEGER NOT NULL, " +
                "installed_date TEXT NOT NULL, " +
                "last_used_date TEXT)";

        String settings = "CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT NOT NULL)";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(activities);
            stmt.execute(goals);
            stmt.execute(apps);
            stmt.execute(settings);
            System.out.println("[Database] initialized: lifesync.db ready.");
        } catch (SQLException e) {
            System.err.println("[Database] initialization failed: " + e.getMessage());
        }
    }
}