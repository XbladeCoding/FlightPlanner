import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Minimal monochrome welcome screen. */
public final class HomePage {
    private HomePage() {}

    public static void launch() {
        SwingUtilities.invokeLater(HomePage::show);
    }

    private static void show() {
        JFrame frame = new JFrame("FlightPlanner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(680, 500));
        frame.setSize(900, 620);
        frame.setLocationRelativeTo(null);

        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Color.WHITE);
        JPanel content = new JPanel(new GridLayout(4, 1, 0, 8));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JLabel title = new JLabel("FlightPlanner", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
        title.setForeground(Color.BLACK);

        JLabel subtitle = new JLabel("by the Caramalized Onions", SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        subtitle.setForeground(new Color(92, 92, 92));

        FlightTrail flightTrail = new FlightTrail();

        RoundedButton getStarted = new RoundedButton("Get Started");
        getStarted.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        getStarted.setForeground(Color.WHITE);
        getStarted.setBackground(Color.BLACK);
        getStarted.setFocusPainted(false);
        getStarted.setOpaque(false);
        getStarted.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        getStarted.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        getStarted.setBorderPainted(false);
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.setBorder(BorderFactory.createEmptyBorder(8, 90, 8, 90));
        buttonRow.add(getStarted, BorderLayout.CENTER);
        getStarted.setMargin(new Insets(8, 16, 8, 16));

        content.add(title);
        content.add(subtitle);
        content.add(flightTrail);
        content.add(buttonRow);
        page.add(content);
        frame.setContentPane(page);
        getStarted.addActionListener(event -> {
            flightTrail.stopAnimation();
            frame.dispose();
            UserInterface.launch();
        });
        frame.setVisible(true);
    }

    private static final class FlightTrail extends JPanel {
        private static final long serialVersionUID = 1L;
        private double progress;
        private final Timer animation;

        private FlightTrail() {
            setOpaque(false);
            setPreferredSize(new Dimension(240, 105));
            animation = new Timer(28, event -> {
                progress += 0.004;
                if (progress > 1.0) progress = 0.0;
                repaint();
            });
            animation.start();
        }

        private void stopAnimation() {
            animation.stop();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double x0 = getWidth() * 0.12, y0 = getHeight() * 0.78;
            double x1 = getWidth() * 0.34, y1 = getHeight() * 0.12;
            double x2 = getWidth() * 0.55, y2 = getHeight() * 0.96;
            double x3 = getWidth() * 0.87, y3 = getHeight() * 0.20;
            Path2D trail = new Path2D.Double();
            int steps = Math.max(2, (int) (progress * 90));
            for (int i = 0; i <= steps; i++) {
                double t = progress * i / steps;
                double[] point = cubicPoint(t, x0, y0, x1, y1, x2, y2, x3, y3);
                if (i == 0) trail.moveTo(point[0], point[1]);
                else trail.lineTo(point[0], point[1]);
            }
            g.setColor(new Color(145, 145, 145));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(trail);

            Path2D plane = new Path2D.Double();
            plane.moveTo(0, -22);
            plane.curveTo(3, -16, 3, -8, 2, -3);
            plane.lineTo(18, 4);
            plane.lineTo(18, 8);
            plane.lineTo(2, 5);
            plane.lineTo(2, 14);
            plane.lineTo(8, 18);
            plane.lineTo(8, 21);
            plane.lineTo(0, 19);
            plane.lineTo(-8, 21);
            plane.lineTo(-8, 18);
            plane.lineTo(-2, 14);
            plane.lineTo(-2, 5);
            plane.lineTo(-18, 8);
            plane.lineTo(-18, 4);
            plane.lineTo(-2, -3);
            plane.curveTo(-3, -8, -3, -16, 0, -22);
            plane.closePath();

            double[] point = cubicPoint(progress, x0, y0, x1, y1, x2, y2, x3, y3);
            double dx = 3 * (1 - progress) * (1 - progress) * (x1 - x0)
                    + 6 * (1 - progress) * progress * (x2 - x1)
                    + 3 * progress * progress * (x3 - x2);
            double dy = 3 * (1 - progress) * (1 - progress) * (y1 - y0)
                    + 6 * (1 - progress) * progress * (y2 - y1)
                    + 3 * progress * progress * (y3 - y2);
            AffineTransform transform = new AffineTransform();
            transform.translate(point[0], point[1]);
            transform.rotate(Math.atan2(dy, dx) + Math.PI / 2);
            g.setColor(Color.BLACK);
            g.fill(transform.createTransformedShape(plane));
            g.dispose();
        }

        private static double[] cubicPoint(double t, double x0, double y0, double x1, double y1,
                double x2, double y2, double x3, double y3) {
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            return new double[]{
                a * x0 + b * x1 + c * x2 + d * x3,
                a * y0 + b * y1 + c * y2 + d * y3
            };
        }
    }
}
