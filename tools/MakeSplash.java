import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generates the start-up splash images (shown by the JVM launcher before any Java code runs):
 *   java tools/MakeSplash.java app/src/main/icons/icon.png app/src/main/splash
 * Writes splash.png (100 %), splash@150pct.png, splash@200pct.png.
 */
public class MakeSplash {
    public static void main(String[] a) throws Exception {
        BufferedImage icon = ImageIO.read(new File(a[0]));
        File out = new File(a[1]);
        out.mkdirs();
        int[][] sizes = {{100, 0}, {150, 1}, {200, 2}};
        for (int[] s : sizes) {
            double k = s[0] / 100.0;
            int w = (int) Math.round(440 * k), h = (int) Math.round(240 * k);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double arc = 16 * k;
            g.setColor(new Color(0x20, 0x20, 0x20));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));
            g.setColor(new Color(0x3A, 0x3A, 0x3A));
            g.setStroke(new BasicStroke((float) Math.max(1, k)));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc));
            int is = (int) Math.round(84 * k);
            g.drawImage(icon, (w - is) / 2, (int) Math.round(40 * k), is, is, null);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI Semibold", Font.PLAIN, (int) Math.round(24 * k)));
            String t = "CloudStream";
            g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, (int) Math.round(164 * k));
            g.setColor(new Color(0xA0, 0xA0, 0xA0));
            g.setFont(new Font("Segoe UI", Font.PLAIN, (int) Math.round(13 * k)));
            t = "Starting...";
            g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, (int) Math.round(190 * k));
            // thin accent bar at the bottom
            g.setColor(new Color(0x00, 0x78, 0xD4));
            int bw = (int) Math.round(120 * k), bh = (int) Math.round(3 * k);
            g.fillRoundRect((w - bw) / 2, (int) Math.round(208 * k), bw, bh, bh, bh);
            g.dispose();
            String name = s[0] == 100 ? "splash.png" : "splash@" + s[0] + "pct.png";
            ImageIO.write(img, "png", new File(out, name));
            System.out.println(name + " " + w + "x" + h);
        }
    }
}
