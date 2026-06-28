package io.github.jean202.heicjpg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Converts a source image and resolves the target name by content: if a JPG with the base
 * name already shows the same picture it is left untouched; if an existing JPG shows a
 * different picture the conversion is written under the next free "-N" variant name.
 * Shared by the CLI and the desktop UI so both behave identically.
 */
final class ContentAwareConverter {
    static final int DEFAULT_THRESHOLD = 5;

    private final ImageConverter converter;
    private final int threshold;

    ContentAwareConverter(ImageConverter converter) {
        this(converter, DEFAULT_THRESHOLD);
    }

    ContentAwareConverter(ImageConverter converter, int threshold) {
        this.converter = converter;
        this.threshold = threshold;
    }

    /** Either {@code written} (a new file was created) or {@code skippedExisting} is set, never both. */
    record Result(Path written, Path skippedExisting) {
        boolean skipped() {
            return written == null;
        }
    }

    Result convert(ConversionTask task, Integer maxDimension) throws IOException, InterruptedException {
        Path baseTarget = task.target();
        Path parent = baseTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temp = (parent != null ? parent : Path.of("."))
                .resolve(".heicjpg-tmp-" + UUID.randomUUID() + ".jpg");
        try {
            converter.convert(new ConversionTask(task.source(), temp), maxDimension);
            long convertedHash = ImageContentHash.dHash(temp);

            for (int index = 0; ; index++) {
                Path candidate = index == 0 ? baseTarget : variantName(baseTarget, index);
                if (!Files.exists(candidate)) {
                    Files.move(temp, candidate);
                    temp = null;
                    return new Result(candidate, null);
                }
                if (sameContent(convertedHash, candidate)) {
                    return new Result(null, candidate);
                }
            }
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private boolean sameContent(long convertedHash, Path candidate) {
        try {
            return ImageContentHash.distance(convertedHash, ImageContentHash.dHash(candidate)) <= threshold;
        } catch (IOException unreadable) {
            // Treat an unreadable existing file as a different image and keep looking.
            return false;
        }
    }

    static Path variantName(Path baseTarget, int index) {
        Path parent = baseTarget.getParent();
        String fileName = baseTarget.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot >= 0 ? fileName.substring(dot) : "";
        String variant = stem + "-" + index + extension;
        return parent != null ? parent.resolve(variant) : Path.of(variant);
    }
}
