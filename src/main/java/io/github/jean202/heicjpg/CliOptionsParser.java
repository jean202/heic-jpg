package io.github.jean202.heicjpg;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class CliOptionsParser {
    private CliOptionsParser() {
    }

    static CliOptions parse(String[] args) {
        List<Path> inputs = new ArrayList<>();
        Path outputDir = null;
        boolean overwrite = false;
        boolean dryRun = false;
        Integer maxDimension = null;
        boolean deleteConverted = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "-h":
                case "--help":
                    return new CliOptions(List.of(), null, false, false, null, false, true);
                case "-o":
                case "--output-dir":
                    index = requireValue(args, index, argument);
                    outputDir = Paths.get(args[index]);
                    break;
                case "--overwrite":
                    overwrite = true;
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                case "--delete-converted":
                    deleteConverted = true;
                    break;
                case "--max-dimension":
                    index = requireValue(args, index, argument);
                    maxDimension = parsePositiveInteger(args[index], argument);
                    break;
                default:
                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + argument);
                    }
                    inputs.add(Paths.get(argument));
            }
        }

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one file or directory path is required.");
        }

        return new CliOptions(List.copyOf(inputs), outputDir, overwrite, dryRun, maxDimension, deleteConverted, false);
    }

    private static int requireValue(String[] args, int currentIndex, String option) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= args.length) {
            throw new IllegalArgumentException("Missing value for option: " + option);
        }
        return nextIndex;
    }

    private static Integer parsePositiveInteger(String rawValue, String option) {
        try {
            int parsed = Integer.parseInt(rawValue);
            if (parsed < 1) {
                throw new IllegalArgumentException(option + " must be a positive integer.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(option + " must be a positive integer.", exception);
        }
    }
}
