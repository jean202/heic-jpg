package io.github.jean202.heicjpg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SipsImageConverter implements ImageConverter {
    @Override
    public void convert(ConversionTask task, Integer maxDimension) throws IOException, InterruptedException {
        Path parent = task.target().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> command = new ArrayList<>();
        command.add("sips");
        command.add("-s");
        command.add("format");
        command.add("jpeg");
        if (maxDimension != null) {
            command.add("-Z");
            command.add(maxDimension.toString());
        }
        command.add(task.source().toString());
        command.add("--out");
        command.add(task.target().toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        try {
            String output;
            try (var inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(formatFailureMessage(task, output));
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }
    }

    private String formatFailureMessage(ConversionTask task, String output) {
        if (output == null || output.isBlank()) {
            return "sips failed for " + task.source();
        }
        return "sips failed for " + task.source() + System.lineSeparator() + output;
    }
}
