package database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class CategorizationDAO {

    public Map<String, Boolean> loadAll() throws SQLException {
        Map<String, Boolean> map = new HashMap<>();
        String sql = "SELECT app_name, is_productive FROM app_categories";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("app_name"), rs.getInt("is_productive") == 1);
            }
        }
        return map;
    }

    public void save(String appName, boolean isProductive) throws SQLException {
        String sql = "INSERT OR REPLACE INTO app_categories(app_name, is_productive) VALUES(?, ?)";
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appName);
            ps.setInt(2, isProductive ? 1 : 0);
            ps.executeUpdate();
        }
    }
}