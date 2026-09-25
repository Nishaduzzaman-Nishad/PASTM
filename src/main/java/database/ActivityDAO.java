package database;

import model.ActivityEntry;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ActivityDAO {

    public void insert(ActivityEntry entry) throws SQLException {
        String sql = "INSERT INTO activities(name, category, duration_seconds, date) VALUES(?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getName());
            ps.setString(2, entry.getCategory());
            ps.setInt(3, entry.getDurationSeconds());
            ps.setString(4, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }

    public List<ActivityEntry> getTodayActivities() throws SQLException {
        String sql = "SELECT name, category, duration_seconds FROM activities " +
                "WHERE date = ? ORDER BY id DESC";
        List<ActivityEntry> list = new ArrayList<>();
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ActivityEntry(
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getInt("duration_seconds")));
                }
            }
        }
        return list;
    }

    public void mergeOrInsert(ActivityEntry entry) throws SQLException {
        String today = LocalDate.now().toString();
        String find = "SELECT id, duration_seconds FROM activities " +
                "WHERE name = ? AND category = ? AND date = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(find)) {
            ps.setString(1, entry.getName());
            ps.setString(2, entry.getCategory());
            ps.setString(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    int newDuration = rs.getInt("duration_seconds") + entry.getDurationSeconds();
                    String update = "UPDATE activities SET duration_seconds = ? WHERE id = ?";
                    try (PreparedStatement ups = conn.prepareStatement(update)) {
                        ups.setInt(1, newDuration);
                        ups.setInt(2, id);
                        ups.executeUpdate();
                    }
                } else {
                    insert(entry);
                }
            }
        }
    }

    public void deleteToday() throws SQLException {
        String sql = "DELETE FROM activities WHERE date = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }

    public void deleteByCategory(String category) throws SQLException {
        String sql = "DELETE FROM activities WHERE category = ? AND date = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }
}