import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * First-run / Settings dialog for configuring organization, services, and windows.
 * Shown automatically when no configuration exists, or via Settings menu.
 */
public class SetupDialog extends JDialog {

    // Organization fields
    private JTextField orgNameField;
    private JTextField orgAddressField;
    private JTextField orgTaglineField;

    // Services table
    private DefaultListModel<String> servicesModel;
    private JList<String> servicesList;
    private JTextField svcCodeField;
    private JTextField svcNameField;
    private JTextField svcDescField;

    // Windows table
    private DefaultListModel<String> windowsModel;
    private JList<String> windowsList;
    private JTextField winNumField;
    private JTextField winNameField;
    private JTextField winDescField;
    private JTextField winServicesField;

    private boolean saved = false;

    public SetupDialog(JFrame parent) {
        super(parent, "System Configuration", true);
        setSize(750, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initUI();
        loadExistingConfig();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        getContentPane().setBackground(new Color(245, 247, 250));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // === TAB 1: Organization ===
        JPanel orgPanel = new JPanel(new GridBagLayout());
        orgPanel.setBackground(new Color(245, 247, 250));
        orgPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        orgNameField = addFormRow(orgPanel, gbc, 0, "Organization Name:", 40);
        orgAddressField = addFormRow(orgPanel, gbc, 1, "Address:", 40);
        orgTaglineField = addFormRow(orgPanel, gbc, 2, "Tagline:", 40);

        tabs.addTab("  Organization  ", orgPanel);

        // === TAB 2: Services ===
        JPanel svcPanel = new JPanel(new BorderLayout(15, 15));
        svcPanel.setBackground(new Color(245, 247, 250));
        svcPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Input fields
        JPanel svcInput = new JPanel(new GridBagLayout());
        svcInput.setBackground(new Color(245, 247, 250));
        GridBagConstraints sGbc = new GridBagConstraints();
        sGbc.insets = new Insets(5, 5, 5, 5);
        sGbc.anchor = GridBagConstraints.WEST;
        svcCodeField = addFormRow(svcInput, sGbc, 0, "Service Code:", 15);
        svcNameField = addFormRow(svcInput, sGbc, 1, "Service Name:", 25);
        svcDescField = addFormRow(svcInput, sGbc, 2, "Description:", 30);

        JButton addSvcBtn = new JButton("Add Service");
        UIUtils.styleButton(addSvcBtn, new Color(70, 130, 200));
        addSvcBtn.addActionListener(e -> addService());
        sGbc.gridy = 3;
        sGbc.gridx = 1;
        svcInput.add(addSvcBtn, sGbc);

        svcPanel.add(svcInput, BorderLayout.NORTH);

        servicesModel = new DefaultListModel<>();
        servicesList = new JList<>(servicesModel);
        servicesList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        servicesList.setFixedCellHeight(28);
        JScrollPane svcScroll = new JScrollPane(servicesList);
        svcScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(170, 190, 210)),
                "Services List",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(40, 80, 120)));
        svcPanel.add(svcScroll, BorderLayout.CENTER);

        JButton delSvcBtn = new JButton("Remove Selected");
        UIUtils.styleButton(delSvcBtn, new Color(200, 70, 70));
        delSvcBtn.addActionListener(e -> removeService());
        JPanel svcBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        svcBtnPanel.setOpaque(false);
        svcBtnPanel.add(delSvcBtn);
        svcPanel.add(svcBtnPanel, BorderLayout.SOUTH);

        tabs.addTab("  Services  ", svcPanel);

        // === TAB 3: Windows ===
        JPanel winPanel = new JPanel(new BorderLayout(15, 15));
        winPanel.setBackground(new Color(245, 247, 250));
        winPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel winInput = new JPanel(new GridBagLayout());
        winInput.setBackground(new Color(245, 247, 250));
        GridBagConstraints wGbc = new GridBagConstraints();
        wGbc.insets = new Insets(5, 5, 5, 5);
        wGbc.anchor = GridBagConstraints.WEST;
        winNumField = addFormRow(winInput, wGbc, 0, "Window #:", 5);
        winNameField = addFormRow(winInput, wGbc, 1, "Window Name:", 20);
        winDescField = addFormRow(winInput, wGbc, 2, "Description:", 30);
        winServicesField = addFormRow(winInput, wGbc, 3, "Service Codes (comma-separated):", 25);

        JLabel hint = new JLabel("Example: PAY,REG  (use service codes from Services tab)");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(Color.GRAY);
        wGbc.gridy = 4; wGbc.gridx = 1;
        winInput.add(hint, wGbc);

        JButton addWinBtn = new JButton("Add Window");
        UIUtils.styleButton(addWinBtn, new Color(70, 130, 200));
        addWinBtn.addActionListener(e -> addWindow());
        wGbc.gridy = 5; wGbc.gridx = 1;
        winInput.add(addWinBtn, wGbc);

        winPanel.add(winInput, BorderLayout.NORTH);

        windowsModel = new DefaultListModel<>();
        windowsList = new JList<>(windowsModel);
        windowsList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        windowsList.setFixedCellHeight(28);
        JScrollPane winScroll = new JScrollPane(windowsList);
        winScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(170, 190, 210)),
                "Windows List",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(40, 80, 120)));
        winPanel.add(winScroll, BorderLayout.CENTER);

        JButton delWinBtn = new JButton("Remove Selected");
        UIUtils.styleButton(delWinBtn, new Color(200, 70, 70));
        delWinBtn.addActionListener(e -> removeWindow());
        JPanel winBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        winBtnPanel.setOpaque(false);
        winBtnPanel.add(delWinBtn);
        winPanel.add(winBtnPanel, BorderLayout.SOUTH);

        tabs.addTab("  Windows  ", winPanel);

        add(tabs, BorderLayout.CENTER);

        // === Bottom Buttons ===
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        btnPanel.setOpaque(false);

        JButton saveBtn = new JButton("Save Configuration");
        UIUtils.styleButton(saveBtn, new Color(46, 125, 50));
        saveBtn.addActionListener(e -> onSave());

        JButton cancelBtn = new JButton("Cancel");
        UIUtils.styleButton(cancelBtn, new Color(150, 150, 150));
        cancelBtn.addActionListener(e -> dispose());

        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private JTextField addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, int cols) {
        gbc.gridy = row; gbc.gridx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(lbl, gbc);
        gbc.gridx = 1;
        JTextField field = new JTextField(cols);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(field, gbc);
        return field;
    }

    private void loadExistingConfig() {
        ConfigService cfg = ConfigService.getInstance();
        Map<String, String> org = cfg.getOrgSettings();
        orgNameField.setText(org.getOrDefault("org_name", ""));
        orgAddressField.setText(org.getOrDefault("address", ""));
        orgTaglineField.setText(org.getOrDefault("tagline", ""));

        for (Map<String, String> s : cfg.getServices()) {
            servicesModel.addElement(s.get("service_code") + " - " + s.get("service_name") +
                    (s.get("description").isEmpty() ? "" : " (" + s.get("description") + ")"));
        }

        for (Map<String, String> w : cfg.getWindows()) {
            windowsModel.addElement("Win " + w.get("window_number") + ": " + w.get("window_name") +
                    " [" + w.get("service_ids") + "]");
        }
    }

    private void addService() {
        String code = svcCodeField.getText().trim().toUpperCase();
        String name = svcNameField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Service code and name are required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        servicesModel.addElement(code + " - " + name +
                (svcDescField.getText().trim().isEmpty() ? "" : " (" + svcDescField.getText().trim() + ")"));
        svcCodeField.setText("");
        svcNameField.setText("");
        svcDescField.setText("");
    }

    private void removeService() {
        int idx = servicesList.getSelectedIndex();
        if (idx >= 0) servicesModel.remove(idx);
    }

    private void addWindow() {
        String num = winNumField.getText().trim();
        String name = winNameField.getText().trim();
        if (num.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Window number and name are required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Integer.parseInt(num);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Window number must be an integer.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String svcIds = winServicesField.getText().trim();
        windowsModel.addElement("Win " + num + ": " + name + " [" + svcIds + "]");
        winNumField.setText("");
        winNameField.setText("");
        winDescField.setText("");
        winServicesField.setText("");
    }

    private void removeWindow() {
        int idx = windowsList.getSelectedIndex();
        if (idx >= 0) windowsModel.remove(idx);
    }

    private void onSave() {
        ConfigService cfg = ConfigService.getInstance();

        // Save org
        cfg.saveOrgSettings(
                orgNameField.getText().trim(),
                orgAddressField.getText().trim(),
                orgTaglineField.getText().trim()
        );

        // Save services
        List<Map<String, String>> svcs = new ArrayList<>();
        for (int i = 0; i < servicesModel.size(); i++) {
            String raw = servicesModel.get(i);
            Map<String, String> s = new HashMap<>();
            String[] parts = raw.split(" - ", 2);
            s.put("service_code", parts[0].trim());
            if (parts.length > 1) {
                String rest = parts[1];
                int paren = rest.indexOf(" (");
                if (paren >= 0) {
                    s.put("service_name", rest.substring(0, paren).trim());
                    s.put("description", rest.substring(paren + 2, rest.length() - 1).trim());
                } else {
                    s.put("service_name", rest.trim());
                    s.put("description", "");
                }
            }
            svcs.add(s);
        }
        cfg.saveServices(svcs);

        // Save windows
        List<Map<String, String>> wins = new ArrayList<>();
        for (int i = 0; i < windowsModel.size(); i++) {
            String raw = windowsModel.get(i);
            Map<String, String> w = new HashMap<>();
            // Format: "Win 1: Name [svc1,svc2]"
            int colon = raw.indexOf(":");
            int bracket = raw.lastIndexOf("[");
            int endBracket = raw.lastIndexOf("]");
            if (colon > 4 && bracket > colon && endBracket > bracket) {
                w.put("window_number", raw.substring(4, colon).trim());
                w.put("window_name", raw.substring(colon + 1, bracket).trim());
                w.put("service_ids", raw.substring(bracket + 1, endBracket).trim());
                w.put("description", "");
            }
            wins.add(w);
        }
        cfg.saveWindows(wins);

        saved = true;
        JOptionPane.showMessageDialog(this, "Configuration saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }
}

