import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class MainFrame extends JFrame {
    private JTabbedPane tabbedPane;
    private StatusBarPanel statusBar;

    public MainFrame() {
        setTitle("Smart Queue Management System v4.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 768));
        setPreferredSize(new Dimension(1400, 900));
        setLocationRelativeTo(null);

        // Main layout
        setLayout(new BorderLayout());

        // Header
        HeaderPanel header = new HeaderPanel();
        add(header, BorderLayout.NORTH);

        // Tabs
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 16));
        tabbedPane.addTab(" Live Queue ", new LiveQueuePanel());
        tabbedPane.addTab(" History ", new QueueHistoryPanel());
        tabbedPane.addTab(" Settings ", new JButton("Config via Web: localhost:8080")); // Web settings link
        add(tabbedPane, BorderLayout.CENTER);

        // Status
        statusBar = new StatusBarPanel();
        add(statusBar, BorderLayout.SOUTH);

        // Responsive resize handling
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
                repaint();
                // Trigger panel refreshes
                statusBar.updateStats();
            }
        });

        pack();
    }
}
