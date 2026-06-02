import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Bottom status bar with real-time queue stats.
 */
public class StatusBarPanel extends JPanel {
    private JLabel statusLabel;
    private javax.swing.Timer refreshTimer;

    public StatusBarPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBackground(new Color(210, 220, 230));
        setBorder(BorderFactory.createEmptyBorder(2, 15, 2, 15));

        statusLabel = new JLabel("Connecting...");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(70, 70, 100));
        add(statusLabel);

        // Refresh every 5s
        refreshTimer = new javax.swing.Timer(5000, e -> updateStats());
        refreshTimer.start();
        updateStats(); // Initial
    }

    public void updateStats() {
        try {
            var service = QueueService.getInstance();
            if (!service.isConnected()) {
                statusLabel.setText("DB Offline");
                statusLabel.setForeground(new Color(200, 70, 70));
                return;
            }

            Map<String, Integer> stats = service.getStats();
            int waiting = stats.getOrDefault("waiting", 0);
            int nextBatch = stats.getOrDefault("nextBatch", 0);
            int served = stats.getOrDefault("servedToday", 0);

            statusLabel.setText(String.format("Live | Waiting: %d | Next Batch: %d | Served today: %d", waiting, nextBatch, served));
            statusLabel.setForeground(new Color(40, 120, 70));
        } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
            statusLabel.setForeground(new Color(200, 70, 70));
        }
    }
}
