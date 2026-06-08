package io.github.jean202.heicjpg;

import java.nio.file.Path;
import java.util.List;

record CliOptions(
        List<Path> inputs,
        Path outputDir,
        boolean overwrite,
        boolean dryRun,
        Integer maxDimension,
        boolean deleteConverted,
        boolean help
) {
}
