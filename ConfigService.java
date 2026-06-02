import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Singleton service for configuration settings (organization, services, windows).
 * Reads/writes to organization_settings, services, and windows tables.
 */
public class ConfigService {
    private static final Logger LOGGER = Logger.getLogger(ConfigService.class.getName());
    private static ConfigService instance;
    private final Connection conn;

    // Cached config
    private Map<String, String> orgSettings;
    private List<Map<String, String>> services;
    private List<Map<String, String>> windows;
    private boolean configLoaded = false;

    private ConfigService() {
        this.conn = QueueService.getInstance().getConnection();
        loadConfig();
    }

    public static ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    public Connection getConnection() {
        return conn;
    }

    /** Load all config from DB into memory cache. */
    public void loadConfig() {
        if (conn == null) {
            LOGGER.warning("DB not connected, cannot load config");
            return;
        }
        try {
            orgSettings = loadOrgSettings();
            services = loadServices();
            windows = loadWindows();
            configLoaded = true;
            LOGGER.info("Config loaded successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config", e);
        }
    }

    private Map<String, String> loadOrgSettings() throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT org_name, address, tagline FROM organization_settings WHERE id = 1")) {
            if (rs.next()) {
                map.put("org_name", rs.getString("org_name"));
                map.put("address", rs.getString("address"));
                map.put("tagline", rs.getString("tagline"));
            } else {
                // Defaults
                map.put("org_name", "Smart Queue Management System");
                map.put("address", "");
                map.put("tagline", "Take a number • Wait • Be served");
            }
        }
        return map;
    }

    private List<Map<String, String>> loadServices() throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, service_code, service_name, description FROM services ORDER BY id")) {
            while (rs.next()) {
                Map<String, String> s = new HashMap<>();
                s.put("id", String.valueOf(rs.getInt("id")));
                s.put("service_code", rs.getString("service_code"));
                s.put("service_name", rs.getString("service_name"));
                s.put("description", rs.getString("description"));
                list.add(s);
            }
        }
        return list;
    }

    private List<Map<String, String>> loadWindows() throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, window_number, window_name, description, service_ids FROM windows ORDER BY window_number")) {
            while (rs.next()) {
                Map<String, String> w = new HashMap<>();
                w.put("id", String.valueOf(rs.getInt("id")));
                w.put("window_number", String.valueOf(rs.getInt("window_number")));
                w.put("window_name", rs.getString("window_name"));
                w.put("description", rs.getString("description"));
                w.put("service_ids", rs.getString("service_ids"));
                list.add(w);
            }
        }
        return list;
    }

    public boolean isConfigLoaded() {
        return configLoaded;
    }

    // ---- Getters ----

    public String getOrgName() {
        return orgSettings != null ? orgSettings.getOrDefault("org_name", "Smart Queue Management System") : "Smart Queue Management System";
    }

    public String getOrgAddress() {
        return orgSettings != null ? orgSettings.getOrDefault("address", "") : "";
    }

    public String getOrgTagline() {
        return orgSettings != null ? orgSettings.getOrDefault("tagline", "Take a number • Wait • Be served") : "Take a number • Wait • Be served";
    }

    public List<Map<String, String>> getServices() {
        return services != null ? new ArrayList<>(services) : new ArrayList<>();
    }

    public List<Map<String, String>> getWindows() {
        return windows != null ? new ArrayList<>(windows) : new ArrayList<>();
    }

    public Map<String, String> getOrgSettings() {
        return orgSettings != null ? new HashMap<>(orgSettings) : new HashMap<>();
    }

    // ---- Setters / Save ----

    public boolean saveOrgSettings(String name, String address, String tagline) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO organization_settings (id, org_name, address, tagline) VALUES (1, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE org_name = ?, address = ?, tagline = ?")) {
            ps.setString(1, name);
            ps.setString(2, address);
            ps.setString(3, tagline);
            ps.setString(4, name);
            ps.setString(5, address);
            ps.setString(6, tagline);
            ps.executeUpdate();
            loadConfig(); // Refresh cache
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Save org settings failed", e);
            return false;
        }
    }

    public boolean saveServices(List<Map<String, String>> newServices) {
        if (conn == null) return false;
        try {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM services");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO services (service_code, service_name, description) VALUES (?, ?, ?)")) {
                for (Map<String, String> s : newServices) {
                    ps.setString(1, s.get("service_code"));
                    ps.setString(2, s.get("service_name"));
                    ps.setString(3, s.getOrDefault("description", ""));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            loadConfig();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) {}
            LOGGER.log(Level.SEVERE, "Save services failed", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {}
        }
    }

    public boolean saveWindows(List<Map<String, String>> newWindows) {
        if (conn == null) return false;
        try {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM windows");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO windows (window_number, window_name, description, service_ids) VALUES (?, ?, ?, ?)")) {
                for (Map<String, String> w : newWindows) {
                    ps.setInt(1, Integer.parseInt(w.get("window_number")));
                    ps.setString(2, w.get("window_name"));
                    ps.setString(3, w.getOrDefault("description", ""));
                    ps.setString(4, w.getOrDefault("service_ids", ""));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            loadConfig();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) {}
            LOGGER.log(Level.SEVERE, "Save windows failed", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) {}
        }
    }

    /** Check if this is the first run (no windows configured yet). */
    public boolean isFirstRun() {
        return windows == null || windows.isEmpty();
    }

    /** Get full config as a nested map for JSON serialization. */
    public Map<String, Object> getFullConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("organization", getOrgSettings());
        config.put("services", getServices());
        config.put("windows", getWindows());
        return config;
    }
}

