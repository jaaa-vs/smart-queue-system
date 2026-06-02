import javax.swing.*;
import java.awt.*;

/**
 * Custom panel with a blue gradient header.
 */
public class HeaderPanel extends JPanel {

    public HeaderPanel() {
        setPreferredSize(new Dimension(1000, 80));
        setLayout(new BorderLayout());

        String orgName = ConfigService.getInstance().getOrgName();
        String orgTagline = ConfigService.getInstance().getOrgTagline();

        JLabel titleLabel = new JLabel("  " + orgName + "  ");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 0));

        JLabel tagline = new JLabel(orgTagline + "  ");
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        tagline.setForeground(new Color(220, 230, 250));
        tagline.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 30));

        add(titleLabel, BorderLayout.WEST);
        add(tagline, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int w = getWidth();
        int h = getHeight();
        Color color1 = new Color(70, 130, 200);
        Color color2 = new Color(30, 70, 120);
        GradientPaint gp = new GradientPaint(0, 0, color1, w, 0, color2);
        g2d.setPaint(gp);
        g2d.fillRect(0, 0, w, h);
    }
}