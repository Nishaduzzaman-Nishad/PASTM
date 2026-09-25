package database;

import model.AppInfo;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AppDAO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String now() { return LocalDateTime.now().format(FMT); }

    public List<AppInfo> getAllSortedByRecent() throws SQLException {
        String sql = "SELECT name, icon, productive, installed_date, last_used_date FROM apps " +
                "ORDER BY (last_used_date IS NULL), last_used_date DESC, installed_date DESC";
        List<AppInfo> list = new ArrayList<>();
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new AppInfo(
                        rs.getString("name"),
                        rs.getString("icon"),
                        rs.getInt("productive") == 1,
                        rs.getString("installed_date"),
                        rs.getString("last_used_date")));
            }
        }
        return list;
    }

    public boolean exists(String name) throws SQLException {
        String sql = "SELECT 1 FROM apps WHERE name = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void insert(AppInfo app) throws SQLException {
        String sql = "INSERT INTO apps(name, icon, productive, installed_date, last_used_date) " +
                "VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app.getName());
            ps.setString(2, app.getIcon());
            ps.setInt(3, app.isProductive() ? 1 : 0);
            ps.setString(4, app.getInstalledDate() != null ? app.getInstalledDate() : now());
            ps.setString(5, app.getLastUsedDate());
            ps.executeUpdate();
        }
    }

    public void markUsed(String name) throws SQLException {
        String sql = "UPDATE apps SET last_used_date = ? WHERE name = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public void updateProductivity(String name, boolean productive) throws SQLException {
        String sql = "UPDATE apps SET productive = ? WHERE name = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productive ? 1 : 0);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public void uninstall(String name) throws SQLException {
        try (Connection conn = Database.connect()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM activities WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM apps WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        }
    }

    public boolean isSeeded() throws SQLException {
        String sql = "SELECT value FROM settings WHERE key = 'apps_seeded'";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    public void markSeeded() throws SQLException {
        String sql = "INSERT OR REPLACE INTO settings(key, value) VALUES('apps_seeded', '1')";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    public void seedDefaultsIfFirstRun() {
        try {
            if (isSeeded()) return;
            insert(new AppInfo("YouTube", "▶", false, now(), null));
            insert(new AppInfo("Facebook", "f", false, now(), null));
            insert(new AppInfo("Instagram", "◎", false, now(), null));
            insert(new AppInfo("WhatsApp", "●", false, now(), null));
            insert(new AppInfo("Chrome", "C", true, now(), null));
            markSeeded();
            System.out.println("[AppDAO] seeded 5 default apps");
        } catch (SQLException ex) {
            System.err.println("[AppDAO] seed failed: " + ex.getMessage());
        }
    }
}