package database;

import model.Goal;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GoalDAO {

    public int insert(Goal goal) throws SQLException {
        String sql = "INSERT INTO goals(name, target_seconds, category, current_seconds, completed, date) " +
                "VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, goal.getName());
            ps.setInt(2, goal.getTargetSeconds());
            ps.setString(3, goal.getCategory());
            ps.setInt(4, goal.getCurrentSeconds());
            ps.setInt(5, goal.isCompleted() ? 1 : 0);
            ps.setString(6, LocalDate.now().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public List<Goal> getTodayGoals() throws SQLException {
        String sql = "SELECT name, target_seconds, category, current_seconds FROM goals " +
                "WHERE date = ? ORDER BY id";
        List<Goal> list = new ArrayList<>();
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int targetMin = rs.getInt("target_seconds") / 60;
                    Goal g = new Goal(rs.getString("name"), targetMin, rs.getString("category"));
                    g.addProgress(rs.getInt("current_seconds"));
                    list.add(g);
                }
            }
        }
        return list;
    }

    public void updateProgress(Goal goal) throws SQLException {
        String sql = "UPDATE goals SET current_seconds = ?, completed = ? " +
                "WHERE name = ? AND category = ? AND date = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, goal.getCurrentSeconds());
            ps.setInt(2, goal.isCompleted() ? 1 : 0);
            ps.setString(3, goal.getName());
            ps.setString(4, goal.getCategory());
            ps.setString(5, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }

    public void delete(Goal goal) throws SQLException {
        String sql = "DELETE FROM goals WHERE name = ? AND category = ?";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goal.getName());
            ps.setString(2, goal.getCategory());
            ps.executeUpdate();
        }
    }

    public void deleteAll() throws SQLException {
        String sql = "DELETE FROM goals";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}