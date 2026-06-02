import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.JOptionPane;

/**
 * Singleton service for queue DB operations.
 * XAMPP MySQL: localhost:3306/root/(empty pass)
 */
public class QueueService {
    private static final Logger LOGGER = Logger.getLogger(QueueService.class.getName());
    private static QueueService instance;
    private Connection conn;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/queue_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    
    private static final int WAITING_CAPACITY = 5;

    private QueueService() {
        connect();
        if (conn != null) {
            runMigrations();
        }
    }

    public static QueueService getInstance() {
        if (instance == null) {
            instance = new QueueService();
        }
        return instance;
    }

    private void connect() {
        try {
            // Explicitly load MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, "root", "");
            LOGGER.info("DB connected");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL driver not found", e);
            JOptionPane.showMessageDialog(null, 
                "MySQL Driver Error: " + e.getMessage() + "\nEnsure mysql-connector-j JAR is in classpath",
                "Driver Not Found", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection failed", e);
            JOptionPane.showMessageDialog(null, 
                "DB Error: " + e.getMessage() + "\nStart XAMPP MySQL & import setup.sql",
                "Connection Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isConnected() {
        return conn != null;
    }

    // Generate next: A + MAX(seq)+1
    public String generateQueueNum(String serviceCode, int windowNum) {
        if (!isConnected()) return null;
        try (PreparedStatement countPs = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'waiting'");
             PreparedStatement insertPs = conn.prepareStatement(
                 "INSERT INTO queues (queue_num, service_code, window_num, status) VALUES (?, ?, ?, ?)"); 
             PreparedStatement seqPs = conn.prepareStatement(
                 "SELECT COALESCE(MAX(CAST(SUBSTRING(queue_num,2) AS UNSIGNED)), 0) + 1 as next_seq FROM queues")) {

            // Get next seq
            ResultSet seqRs = seqPs.executeQuery();
            seqRs.next();
            int nextSeq = seqRs.getInt("next_seq");
            String newNum = String.format("A%03d", nextSeq);

            // Check capacity
            ResultSet countRs = countPs.executeQuery();
            countRs.next();
            int waitingCount = countRs.getInt(1);
            String status = (waitingCount < WAITING_CAPACITY) ? "waiting" : "next_batch";

            insertPs.setString(1, newNum);
            insertPs.setString(2, serviceCode);
            insertPs.setInt(3, windowNum);
            insertPs.setString(4, status);
            insertPs.executeUpdate();
            return newNum;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Generate failed", e);
            return null;
        }
    }
    
    // Legacy method
    public String generateQueueNum() {
        return generateQueueNum("GEN", 0);
    }

    // Get next waiting → set 'calling'
    public String callNext() {
        if (!isConnected()) return null;
        try (PreparedStatement ps1 = conn.prepareStatement(
            "UPDATE queues SET status = 'calling', called_time = CURRENT_TIMESTAMP WHERE status = 'waiting' ORDER BY gen_time ASC LIMIT 1");
             PreparedStatement ps2 = conn.prepareStatement(
                 "UPDATE queues SET status = 'calling', called_time = CURRENT_TIMESTAMP WHERE status = 'next_batch' ORDER BY gen_time ASC LIMIT 1");
             PreparedStatement getPs = conn.prepareStatement(
                 "SELECT queue_num FROM queues WHERE status = 'calling' ORDER BY called_time DESC LIMIT 1")) {

            // Try waiting first
            int updated = ps1.executeUpdate();

            if (updated == 0) {
                // Then next_batch
                updated = ps2.executeUpdate();
            }
            
            if (updated > 0) {
                ResultSet rs = getPs.executeQuery();
                if (rs.next()) {
                    String calledNum = rs.getString("queue_num");
                    return calledNum;
                }
            }
            return null;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Call next failed", e);
            return null;
        }
    }

    /**
     * Transfer next_batch to waiting when current waiting is empty.
     * Called automatically after serveCurrent or manually.
     */
    public boolean transferNextBatchToWaiting() {
        if (!isConnected()) return false;
        
        // Check if waiting is empty
        try (PreparedStatement checkPs = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'waiting'");
             PreparedStatement transferPs = conn.prepareStatement("UPDATE queues SET status = 'waiting' WHERE status = 'next_batch' ORDER BY gen_time ASC LIMIT ?")) {
            
            ResultSet checkRs = checkPs.executeQuery();
            checkRs.next();
            int waitingCount = checkRs.getInt(1);
            
            if (waitingCount == 0) {
                transferPs.setInt(1, WAITING_CAPACITY);
                int transferred = transferPs.executeUpdate();
                System.out.println("Transferred " + transferred + " from next_batch to waiting");
                return transferred > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Transfer next_batch failed", e);
        }
        return false;
    }

    // Serve current calling → 'served'
    public boolean serveCurrent(String queueNum) {
        if (!isConnected()) return false;
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE queues SET status = 'served' WHERE queue_num = ? AND status = 'calling'")) {
            ps.setString(1, queueNum);
            int updated = ps.executeUpdate();
            boolean served = updated > 0;
            
            if (served) {
                // Auto-transfer next_batch to waiting if empty
                transferNextBatchToWaiting();
            }
            return served;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Serve failed", e);
            return false;
        }
    }

    // Live queue data
    public List<String> getWaitingQueue() {
        List<String> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num FROM queues WHERE status = 'waiting' ORDER BY gen_time ASC LIMIT ?")) {
            ps.setInt(1, WAITING_CAPACITY);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("queue_num"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load waiting failed", e);
        }
        return result;
    }

    // Detailed waiting entries with service and window
    public List<Map<String, Object>> getWaitingQueueDetailed() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num, service_code, window_num FROM queues WHERE status = 'waiting' ORDER BY gen_time ASC LIMIT ?")) {
            ps.setInt(1, WAITING_CAPACITY);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("num", rs.getString("queue_num"));
                row.put("service", rs.getString("service_code"));
                row.put("window", rs.getInt("window_num"));
                result.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load waiting detailed failed", e);
        }
        return result;
    }

    public List<String> getCallingQueue() {
        List<String> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num FROM queues WHERE status = 'calling' ORDER BY called_time DESC LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result.add(rs.getString("queue_num"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load calling failed", e);
        }
        return result;
    }

    public List<Map<String, Object>> getCallingQueueDetailed() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num, service_code, window_num FROM queues WHERE status = 'calling' ORDER BY called_time DESC LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("num", rs.getString("queue_num"));
                row.put("service", rs.getString("service_code"));
                row.put("window", rs.getInt("window_num"));
                result.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load calling detailed failed", e);
        }
        return result;
    }

    public List<String> getNextBatch(int count) {
        List<String> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num FROM queues WHERE status = 'next_batch' ORDER BY gen_time ASC LIMIT ?")) {
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("queue_num"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load next_batch failed", e);
        }
        return result;
    }

    public List<Map<String, Object>> getNextBatchDetailed(int count) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num, service_code, window_num FROM queues WHERE status = 'next_batch' ORDER BY gen_time ASC LIMIT ?")) {
            ps.setInt(1, count);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("num", rs.getString("queue_num"));
                row.put("service", rs.getString("service_code"));
                row.put("window", rs.getInt("window_num"));
                result.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load next_batch detailed failed", e);
        }
        return result;
    }

    private List<String> getQueuesByStatus(String status) {
        List<String> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT queue_num FROM queues WHERE status = ? ORDER BY gen_time ASC LIMIT 10")) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("queue_num"));
            }
            System.out.println("DEBUG QueueService.getQueuesByStatus('" + status + "'): found " + result.size() + " items: " + result);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Load " + status + " failed", e);
            System.out.println("DEBUG QueueService.getQueuesByStatus('" + status + "'): SQL error: " + e.getMessage());
        }
        return result;
    }

    // History: last 20 served + recent
    public List<Object[]> getHistory() {
        List<Object[]> result = new ArrayList<>();
        if (!isConnected()) return result;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT queue_num, gen_time, called_time, status " +
                "FROM queues ORDER BY gen_time DESC LIMIT 20");
            while (rs.next()) {
                result.add(new Object[] {
                    rs.getString("queue_num"),
                    rs.getString("gen_time").substring(11, 16), // HH:MM
                    rs.getString("called_time") != null ? rs.getString("called_time").substring(11, 16) : "-",
                    rs.getString("status").toUpperCase()
                });
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "History failed", e);
        }
        return result;
    }

    // Stats: waiting count, served today
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        if (!isConnected()) {
            stats.put("waiting", 0);
            stats.put("nextBatch", 0);
            stats.put("servedToday", 0);
            return stats;
        }
        try (PreparedStatement ps1 = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'waiting'");
             PreparedStatement ps2 = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'next_batch'");
             PreparedStatement ps3 = conn.prepareStatement(
                 "SELECT COUNT(*) FROM queues WHERE status = 'served' AND DATE(gen_time) = CURDATE()")) {

            ResultSet rs1 = ps1.executeQuery();
            rs1.next();
            stats.put("waiting", rs1.getInt(1));

            ResultSet rs2 = ps2.executeQuery();
            rs2.next();
            stats.put("nextBatch", rs2.getInt(1));
            
            ResultSet rs3 = ps3.executeQuery();
            rs3.next();
            stats.put("servedToday", rs3.getInt(1));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Stats failed", e);
        }
        return stats;
    }

    // Insights: live operational recommendations for presentation and operations
    public Map<String, Object> getQueueInsights() {
        Map<String, Object> insights = new HashMap<>();
        List<Map<String, Object>> waitEstimates = new ArrayList<>();

        insights.put("throughputPerHour", 0.0);
        insights.put("slotSeconds", 120);
        insights.put("avgWaitSeconds", 0);
        insights.put("recommendationLevel", "low");
        insights.put("recommendation", "Flow is healthy. Keep current staffing.");
        insights.put("waitEstimates", waitEstimates);

        if (!isConnected()) {
            insights.put("recommendationLevel", "high");
            insights.put("recommendation", "Database is offline. Restore DB connection first.");
            return insights;
        }

        int waitingCount = 0;
        int nextBatchCount = 0;
        int servedToday = 0;
        int avgWaitSeconds = 0;
        double throughputPerHour = 0.0;
        int slotSeconds = 120;

        try (PreparedStatement waitingPs = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'waiting'");
             PreparedStatement nextPs = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'next_batch'");
             PreparedStatement servedPs = conn.prepareStatement("SELECT COUNT(*) FROM queues WHERE status = 'served' AND DATE(gen_time) = CURDATE()");
             PreparedStatement avgWaitPs = conn.prepareStatement(
                 "SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, gen_time, called_time)), 0) FROM queues WHERE status = 'served' AND called_time IS NOT NULL AND DATE(gen_time) = CURDATE()");
             PreparedStatement firstGenPs = conn.prepareStatement("SELECT MIN(gen_time) FROM queues WHERE DATE(gen_time) = CURDATE()");
             PreparedStatement waitingListPs = conn.prepareStatement(
                 "SELECT queue_num FROM queues WHERE status = 'waiting' ORDER BY gen_time ASC LIMIT ?")) {

            ResultSet waitingRs = waitingPs.executeQuery();
            if (waitingRs.next()) waitingCount = waitingRs.getInt(1);

            ResultSet nextRs = nextPs.executeQuery();
            if (nextRs.next()) nextBatchCount = nextRs.getInt(1);

            ResultSet servedRs = servedPs.executeQuery();
            if (servedRs.next()) servedToday = servedRs.getInt(1);

            ResultSet avgWaitRs = avgWaitPs.executeQuery();
            if (avgWaitRs.next()) avgWaitSeconds = avgWaitRs.getInt(1);

            Timestamp firstGen = null;
            ResultSet firstGenRs = firstGenPs.executeQuery();
            if (firstGenRs.next()) firstGen = firstGenRs.getTimestamp(1);

            if (firstGen != null) {
                long elapsedSeconds = Math.max(60, (System.currentTimeMillis() - firstGen.getTime()) / 1000);
                throughputPerHour = (servedToday * 3600.0) / elapsedSeconds;
            }

            if (throughputPerHour > 0.05) {
                slotSeconds = Math.max(35, (int) Math.round(3600.0 / throughputPerHour));
            }

            if (avgWaitSeconds > 0) {
                slotSeconds = (slotSeconds + Math.max(35, avgWaitSeconds / Math.max(1, waitingCount == 0 ? 1 : waitingCount))) / 2;
            }

            waitingListPs.setInt(1, WAITING_CAPACITY);
            ResultSet waitListRs = waitingListPs.executeQuery();

            int position = 1;
            while (waitListRs.next()) {
                String num = waitListRs.getString("queue_num");
                int etaSeconds = slotSeconds * position;
                int etaMinutes = (int) Math.ceil(etaSeconds / 60.0);
                Map<String, Object> row = new HashMap<>();
                row.put("num", num);
                row.put("etaMinutes", etaMinutes);
                row.put("etaLabel", etaMinutes <= 1 ? "about 1 min" : "about " + etaMinutes + " min");
                waitEstimates.add(row);
                position++;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Insights computation failed", e);
            insights.put("recommendationLevel", "high");
            insights.put("recommendation", "Unable to compute insights. Check DB query health.");
            return insights;
        }

        String recommendationLevel = "low";
        String recommendation = "Flow is healthy. Keep current staffing.";

        if (waitingCount >= WAITING_CAPACITY || nextBatchCount >= 3) {
            recommendationLevel = "high";
            recommendation = "Queue surge detected. Open one extra service window now.";
        } else if (waitingCount >= 3 || (waitingCount >= 2 && throughputPerHour < 10.0)) {
            recommendationLevel = "medium";
            recommendation = "Flow is slowing. Prioritize fast transactions for the next 10 minutes.";
        }

        insights.put("throughputPerHour", Math.round(throughputPerHour * 10.0) / 10.0);
        insights.put("slotSeconds", slotSeconds);
        insights.put("avgWaitSeconds", avgWaitSeconds);
        insights.put("waitingCount", waitingCount);
        insights.put("nextBatchCount", nextBatchCount);
        insights.put("servedToday", servedToday);
        insights.put("recommendationLevel", recommendationLevel);
        insights.put("recommendation", recommendation);
        insights.put("waitEstimates", waitEstimates);
        return insights;
    }

    // Reset: clear waiting
    public void resetQueue() {
        if (!isConnected()) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM queues");  // Clear ALL queues, restart at A001
            System.out.println("Queue reset complete - next number will be A001");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Reset failed", e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Close failed", e);
        }
    }

    private void runMigrations() {
        try (Statement stmt = conn.createStatement()) {
            // Add missing columns to queues table if they don't exist
            try {
                stmt.executeUpdate("ALTER TABLE queues ADD COLUMN service_code VARCHAR(20) DEFAULT ''");
                LOGGER.info("Migration: Added service_code column to queues table");
            } catch (SQLException e) {
                // Column likely already exists, ignore
            }
            
            try {
                stmt.executeUpdate("ALTER TABLE queues ADD COLUMN window_num INT DEFAULT 0");
                LOGGER.info("Migration: Added window_num column to queues table");
            } catch (SQLException e) {
                // Column likely already exists, ignore
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Migration failed", e);
        }
    }
}

