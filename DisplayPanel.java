import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import javax.swing.Timer;

/**
 * HDMI Display: NOW SERVING + Current Waiting (left) + Next Batch (right).
 */
public class DisplayPanel extends JPanel {
    private JLabel numberDisplay;
    private DefaultListModel<String> waitingModel;
    private JList<String> waitingList;
    private DefaultListModel<String> nextModel;
    private JList<String> nextList;
    private Timer refreshTimer;

    public DisplayPanel() {
        setBackground(new Color(20, 30, 45));
        setLayout(new BorderLayout());

        // Now Serving top
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.setOpaque(false);
        JLabel servingLabel = new JLabel("Now Serving");
        servingLabel.setFont(new Font("Segoe UI", Font.BOLD, 64));
        servingLabel.setForeground(new Color(100, 255, 150));
        topPanel.add(servingLabel);

        numberDisplay = new JLabel(" - ");
        numberDisplay.setFont(new Font("Segoe UI", Font.BOLD, 300));
        numberDisplay.setForeground(Color.WHITE);
        numberDisplay.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(numberDisplay);
        add(topPanel, BorderLayout.NORTH);

        // 2 Lists SIDE BY SIDE
        JPanel listsContainer = new JPanel(new GridLayout(1, 2, 20, 0));
        listsContainer.setOpaque(false);

        // Waiting LEFT
        JPanel waitingPanel = createListPanel("Current Waiting");
        waitingModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingModel);
        styleList(waitingList);
        waitingPanel.add(new JScrollPane(waitingList));
        listsContainer.add(waitingPanel);

        // Next Batch RIGHT
        JPanel nextPanel = createListPanel("Next Batch");
        nextModel = new DefaultListModel<>();
        nextList = new JList<>(nextModel);
        styleList(nextList);
        nextPanel.add(new JScrollPane(nextList));
        listsContainer.add(nextPanel);

        add(listsContainer, BorderLayout.CENTER);

        refreshTimer = new Timer(1500, e -> refreshData());
        refreshTimer.start();
        refreshData();
    }

    private JPanel createListPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 40, 60));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 36));
        label.setForeground(Color.WHITE);
        label.setOpaque(false);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    private void styleList(JList<String> list) {
        list.setFont(new Font("Segoe UI", Font.BOLD, 64));
        list.setFixedCellHeight(100);
        list.setBackground(new Color(40, 50, 70));
        list.setForeground(Color.WHITE);
        list.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        list.setSelectionBackground(new Color(60, 70, 90));
        list.setVisibleRowCount(-1);
    }

    private void refreshData() {
        QueueService service = QueueService.getInstance();
        if (!service.isConnected()) {
            numberDisplay.setText("Database Offline");
            numberDisplay.setForeground(Color.RED);
            waitingModel.clear();
            nextModel.clear();
            return;
        }

        // Serving
        List<String> calling = service.getCallingQueue();
        numberDisplay.setText(calling.isEmpty() ? "-" : calling.get(0));
        numberDisplay.setForeground(calling.isEmpty() ? Color.GRAY : new Color(100, 255, 150));

        // Waiting
        waitingModel.clear();
        service.getWaitingQueue().forEach(waitingModel::addElement);

        // Next batch
        nextModel.clear();
        service.getNextBatch(8).forEach(nextModel::addElement);
    }
}

