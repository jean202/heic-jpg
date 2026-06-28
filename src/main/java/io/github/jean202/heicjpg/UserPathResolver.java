package io.github.jean202.heicjpg;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;

final class UserPathResolver {
    private UserPathResolver() {
    }

    static Path resolve(Path rawPath) throws IOException {
        Path absolutePath = rawPath.toAbsolutePath().normalize();
        Path root = absolutePath.getRoot();
        if (root == null) {
            return absolutePath;
        }

        Path current = root;
        for (int index = 0; index < absolutePath.getNameCount(); index++) {
            String segment = absolutePath.getName(index).toString();
            Path directCandidate = current.resolve(segment);
            if (Files.exists(directCandidate)) {
                // On normalization-insensitive filesystems (e.g. APFS) Files.exists succeeds
                // for a Unicode-composed (NFC) segment even when the on-disk entry is stored
                // decomposed (NFD). Resolve to the actual filesystem spelling so downstream
                // path equality (dedup, output collision) compares the real entry.
                current = actualSpelling(current, directCandidate);
                continue;
            }

            if (!Files.isDirectory(current)) {
                current = appendRemaining(current, absolutePath, index);
                break;
            }

            Path normalizedMatch = findNormalizedMatch(current, segment);
            if (normalizedMatch != null) {
                current = normalizedMatch;
                continue;
            }

            current = appendRemaining(current, absolutePath, index);
            break;
        }

        return current.normalize();
    }

    private static Path actualSpelling(Path parent, Path candidate) {
        try {
            Path realName = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS).getFileName();
            return realName == null ? candidate : parent.resolve(realName);
        } catch (IOException e) {
            return candidate;
        }
    }

    private static Path appendRemaining(Path current, Path fullPath, int startIndex) {
        Path resolved = current;
        for (int index = startIndex; index < fullPath.getNameCount(); index++) {
            resolved = resolved.resolve(fullPath.getName(index).toString());
        }
        return resolved;
    }

    private static Path findNormalizedMatch(Path directory, String requestedSegment) throws IOException {
        String requestedNfc = normalize(requestedSegment);
        Path matched = null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                String childName = child.getFileName().toString();
                if (!normalize(childName).equals(requestedNfc)) {
                    continue;
                }

                if (matched != null) {
                    return null;
                }
                matched = child;
            }
        }

        return matched;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
