import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * No-dependency visual difference check for two PNG screenshots.
 * Usage: java tools/CompareScreenshots.java before.png after.png [maxRms=0.03]
 * Exit: 0 match, 2 significant difference, 3 unreadable/incompatible.
 */
public final class CompareScreenshots {
    private CompareScreenshots() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java tools/CompareScreenshots.java before.png after.png [maxRms]");
            System.exit(3);
        }
        double threshold = args.length == 3 ? Double.parseDouble(args[2]) : 0.03;
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            System.err.println("maxRms must be between 0.0 and 1.0");
            System.exit(3);
        }
        BufferedImage original = ImageIO.read(Path.of(args[0]).toFile());
        BufferedImage candidate = ImageIO.read(Path.of(args[1]).toFile());
        if (original == null || candidate == null
                || original.getWidth() != candidate.getWidth()
                || original.getHeight() != candidate.getHeight()) {
            System.err.println("Incomparable images: unreadable or different dimensions");
            System.exit(3);
        }
        long squaredError = 0;
        long changedPixels = 0;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int left = original.getRGB(x, y);
                int right = candidate.getRGB(x, y);
                boolean changed = false;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int delta = ((left >>> shift) & 255) - ((right >>> shift) & 255);
                    squaredError += (long) delta * delta;
                    if (Math.abs(delta) >= 8) {
                        changed = true;
                    }
                }
                if (changed) {
                    changedPixels++;
                }
            }
        }
        long pixels = (long) original.getWidth() * original.getHeight();
        double rms = Math.sqrt(squaredError / (3.0 * pixels)) / 255.0;
        double changedFraction = changedPixels / (double) pixels;
        System.out.printf(java.util.Locale.ROOT,
                "{\"rms\":%.6f,\"changedFraction\":%.6f,\"threshold\":%.6f,\"status\":\"%s\"}%n",
                rms, changedFraction, threshold, rms <= threshold ? "pass" : "regression");
        System.exit(rms <= threshold ? 0 : 2);
    }
}
