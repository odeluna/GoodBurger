package rewards;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Native 128/64/32 artwork and alpha masks from the Go project's sprite pipeline. */
public final class SpriteAtlas {
    private final int[] sizes = {128, 64, 32};
    private final int[][][] pixels = new int[3][4][];
    private WritableImage[][] images;
    private final WritableImage[][] drowningTints = new WritableImage[3][4];

    public SpriteAtlas() throws IOException {
        for (int stage = 0; stage < 2; stage++) {
            int size = sizes[stage];
            String prefix = stage == 0 ? "noir-native-128-" : "ocean-native-64-";
            BufferedImage right = read(prefix + "right.png");
            BufferedImage left = read(prefix + "left.png");
            for (int frame = 0; frame < 4; frame++) {
                int[] argb = new int[size * size];
                BufferedImage source = frame % 2 == 0 ? right : left;
                int bob = switch (frame) { case 1 -> -1; case 3 -> 1; default -> 0; };
                for (int y = 0; y < size; y++) {
                    int sourceY = y - bob;
                    if (sourceY < 0 || sourceY >= size) continue;
                    for (int x = 0; x < size; x++) {
                        int sourceX = frame % 2 == 0 ? x : size - 1 - x;
                        int color = source.getRGB(sourceX, sourceY);
                        if (stage == 0) color = grayscale(color);
                        argb[y * size + x] = color;
                    }
                }
                pixels[stage][frame] = argb;
            }
        }
        BufferedImage underwater = read("player-run.png");
        for (int frame = 0; frame < 4; frame++) {
            int[] argb = new int[32 * 32];
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    argb[y * 32 + x] = underwater.getRGB(frame * 32 + x, 64 + y);
                }
            }
            pixels[2][frame] = argb;
        }
    }

    private static BufferedImage read(String name) throws IOException {
        try (InputStream stream = SpriteAtlas.class.getResourceAsStream("/rewards/assets/" + name)) {
            if (stream == null) throw new IOException("Missing sprite: " + name);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Unreadable sprite: " + name);
            return image;
        }
    }

    private static int grayscale(int argb) {
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        int gray = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
        return (argb & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
    }

    public int size(int stage) { return sizes[Math.min(stage, 2)]; }

    public boolean opaque(int stage, int frame, boolean facingLeft, int x, int y) {
        int size = size(stage);
        if (facingLeft) x = size - 1 - x;
        return ((pixels[Math.min(stage, 2)][frame % 4][y * size + x] >>> 24) & 255) >= 128;
    }

    /** JavaFX is used for every on-screen sprite; ImageIO only decodes the source PNGs. */
    public WritableImage image(int stage, int frame) {
        if (images == null) {
            images = new WritableImage[3][4];
            for (int s = 0; s < 3; s++) {
                for (int f = 0; f < 4; f++) {
                    int size = sizes[s];
                    WritableImage image = new WritableImage(size, size);
                    image.getPixelWriter().setPixels(0, 0, size, size,
                            PixelFormat.getIntArgbInstance(), pixels[s][f], 0, size);
                    images[s][f] = image;
                }
            }
        }
        return images[Math.min(stage, 2)][frame % 4];
    }

    public WritableImage drowningImage(int frame, int tintIndex) {
        int tint = Math.floorMod(tintIndex, 3), pose = frame % 4;
        if (drowningTints[tint][pose] == null) {
            int[] color = {0xFF6347, 0x00FFAA, 0xFFB71F};
            int tintRgb = color[tint];
            int tr = (tintRgb >>> 16) & 255, tg = (tintRgb >>> 8) & 255, tb = tintRgb & 255;
            int[] tinted = new int[32 * 32];
            for (int i = 0; i < tinted.length; i++) {
                int source = pixels[2][pose][i];
                int r = ((source >>> 16) & 255) * tr / 255;
                int g = ((source >>> 8) & 255) * tg / 255;
                int b = (source & 255) * tb / 255;
                tinted[i] = (source & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
            WritableImage image = new WritableImage(32, 32);
            image.getPixelWriter().setPixels(0, 0, 32, 32,
                    PixelFormat.getIntArgbInstance(), tinted, 0, 32);
            drowningTints[tint][pose] = image;
        }
        return drowningTints[tint][pose];
    }
}
