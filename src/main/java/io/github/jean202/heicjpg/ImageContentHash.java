package io.github.jean202.heicjpg;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Perceptual (difference) hash for deciding whether two images show the same picture,
 * regardless of size or JPEG re-encoding. Images are reduced to a 9x8 grayscale grid and
 * each pixel is compared with its right neighbour, yielding a 64-bit fingerprint.
 */
final class ImageContentHash {
    private static final int WIDTH = 9;
    private static final int HEIGHT = 8;

    private ImageContentHash() {
    }

    static long dHash(Path imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile.toFile());
        if (image == null) {
            throw new IOException("Unsupported or unreadable image: " + imageFile);
        }

        int[] gray = downscaleToGray(image);
        long hash = 0L;
        int bit = 0;
        for (int row = 0; row < HEIGHT; row++) {
            for (int col = 0; col < WIDTH - 1; col++) {
                int left = gray[row * WIDTH + col];
                int right = gray[row * WIDTH + col + 1];
                if (left > right) {
                    hash |= (1L << bit);
                }
                bit++;
            }
        }
        return hash;
    }

    static int distance(long first, long second) {
        return Long.bitCount(first ^ second);
    }

    private static int[] downscaleToGray(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        graphics.dispose();

        int[] gray = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y * WIDTH + x] = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }
}
