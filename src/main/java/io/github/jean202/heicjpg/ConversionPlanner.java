package io.github.jean202.heicjpg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class ConversionPlanner {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("heic", "heif");

    ConversionPlan plan(CliOptions options) throws IOException {
        List<ConversionTask> tasks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<Path, Path> outputToSource = new LinkedHashMap<>();
        Set<Path> seenSources = new LinkedHashSet<>();
        Path outputDir = options.outputDir() == null ? null : UserPathResolver.resolve(options.outputDir());

        for (Path rawInput : options.inputs()) {
            Path input = UserPathResolver.resolve(rawInput);

            if (!Files.exists(input)) {
                errors.add("Input not found: " + input);
                continue;
            }

            if (Files.isRegularFile(input)) {
                planFileInput(input, outputDir, tasks, errors, outputToSource, seenSources);
                continue;
            }

            if (Files.isDirectory(input)) {
                planDirectoryInput(input, outputDir, tasks, errors, outputToSource, seenSources);
                continue;
            }

            errors.add("Unsupported input type: " + input);
        }

        tasks.sort(Comparator.comparing(task -> task.source().toString()));
        return new ConversionPlan(List.copyOf(tasks), List.copyOf(errors));
    }

    private void planFileInput(
            Path input,
            Path outputDir,
            List<ConversionTask> tasks,
            List<String> errors,
            Map<Path, Path> outputToSource,
            Set<Path> seenSources
    ) {
        if (!isConvertible(input)) {
            errors.add("Unsupported file extension (expected .heic or .heif): " + input);
            return;
        }

        Path target = buildTargetForFile(input, outputDir);
        registerTask(new ConversionTask(input, target), tasks, errors, outputToSource, seenSources);
    }

    private void planDirectoryInput(
            Path directory,
            Path outputDir,
            List<ConversionTask> tasks,
            List<String> errors,
            Map<Path, Path> outputToSource,
            Set<Path> seenSources
    ) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isConvertible)
                    .sorted()
                    .forEach(path -> {
                        Path target = buildTargetForDirectory(directory, path, outputDir);
                        registerTask(new ConversionTask(path, target), tasks, errors, outputToSource, seenSources);
                    });
        }
    }

    private void registerTask(
            ConversionTask task,
            List<ConversionTask> tasks,
            List<String> errors,
            Map<Path, Path> outputToSource,
            Set<Path> seenSources
    ) {
        Path source = task.source().toAbsolutePath().normalize();
        if (!seenSources.add(source)) {
            return;
        }

        Path target = task.target().toAbsolutePath().normalize();
        Path existingSource = outputToSource.putIfAbsent(target, source);
        if (existingSource != null && !existingSource.equals(source)) {
            errors.add("Output collision: " + target + " would be written by both " + existingSource + " and " + source);
            return;
        }

        tasks.add(new ConversionTask(source, target));
    }

    private Path buildTargetForFile(Path input, Path outputDir) {
        Path convertedName = replaceExtension(input.getFileName());
        if (outputDir == null) {
            Path parent = input.getParent();
            return parent == null ? convertedName : parent.resolve(convertedName);
        }
        return outputDir.resolve(convertedName);
    }

    private Path buildTargetForDirectory(Path directory, Path input, Path outputDir) {
        if (outputDir == null) {
            return input.resolveSibling(replaceExtension(input.getFileName()));
        }

        Path relativePath = directory.relativize(input);
        Path relativeTarget = replaceExtension(relativePath);
        Path directoryName = directory.getFileName();
        if (directoryName == null) {
            return outputDir.resolve(relativeTarget);
        }
        return outputDir.resolve(directoryName).resolve(relativeTarget);
    }

    private boolean isConvertible(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private Path replaceExtension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return path;
        }

        String rawName = fileName.toString();
        int dotIndex = rawName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? rawName.substring(0, dotIndex) : rawName;
        String convertedName = baseName + ".jpg";
        Path parent = path.getParent();
        return parent == null ? Path.of(convertedName) : parent.resolve(convertedName);
    }
}
