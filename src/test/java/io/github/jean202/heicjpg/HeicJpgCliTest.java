package io.github.jean202.heicjpg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class HeicJpgCliTest {
    private HeicJpgCliTest() {
    }

    public static void main(String[] args) throws Exception {
        shouldPlanDirectoryRecursively();
        shouldSkipExistingTargetsWithoutOverwrite();
        shouldSupportDryRunWithoutCallingConverter();
        shouldRejectUnsupportedFileInput();
        shouldResolveUnicodeNormalizedMacPaths();
        System.out.println("All tests passed.");
    }

    private static void shouldPlanDirectoryRecursively() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-plan");
        try {
            Path sourceRoot = Files.createDirectories(tempDir.resolve("iphone"));
            Files.writeString(sourceRoot.resolve("IMG_0001.HEIC"), "one");
            Path nested = Files.createDirectories(sourceRoot.resolve("trip/day1"));
            Files.writeString(nested.resolve("IMG_0002.heif"), "two");
            Files.writeString(sourceRoot.resolve("ignore.txt"), "skip");

            CliOptions options = new CliOptions(
                    List.of(sourceRoot),
                    tempDir.resolve("out"),
                    false,
                    false,
                    null,
                    false
            );

            ConversionPlan plan = new ConversionPlanner().plan(options);

            assertEquals(2, plan.tasks().size(), "Planner should find two convertible files.");
            assertTrue(plan.errors().isEmpty(), "Planner should not report validation errors.");
            assertEquals(
                    tempDir.resolve("out/iphone/IMG_0001.jpg").toAbsolutePath().normalize(),
                    plan.tasks().get(0).target(),
                    "Planner should preserve the directory root under output-dir."
            );
            assertEquals(
                    tempDir.resolve("out/iphone/trip/day1/IMG_0002.jpg").toAbsolutePath().normalize(),
                    plan.tasks().get(1).target(),
                    "Planner should preserve nested directories under output-dir."
            );
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldSkipExistingTargetsWithoutOverwrite() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-skip");
        try {
            Path source = tempDir.resolve("IMG_1001.HEIC");
            Path target = tempDir.resolve("IMG_1001.jpg");
            Files.writeString(source, "source");
            Files.writeString(target, "existing");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, source.toString());

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Skipping existing output should still succeed.");
            assertEquals(0, converter.calls.size(), "Converter must not run when the target already exists.");
            assertContains(result.stdout(), "SKIP", "CLI should explain that the target was skipped.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldSupportDryRunWithoutCallingConverter() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-dry");
        try {
            Path source = tempDir.resolve("IMG_2001.heic");
            Path outputDir = tempDir.resolve("converted");
            Files.writeString(source, "source");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(
                    converter,
                    "--dry-run",
                    "--output-dir", outputDir.toString(),
                    source.toString()
            );

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Dry-run should succeed.");
            assertEquals(0, converter.calls.size(), "Dry-run must not invoke the converter.");
            assertContains(result.stdout(), "PLAN", "Dry-run should print planned work.");
            assertContains(result.stdout(), "Planned 1 conversion(s)", "Dry-run summary should include the planned count.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldRejectUnsupportedFileInput() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-invalid");
        try {
            Path source = tempDir.resolve("notes.txt");
            Files.writeString(source, "not an image");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, source.toString());

            assertEquals(HeicJpgCli.EXIT_USAGE, result.exitCode(), "Unsupported file input should be treated as a usage error.");
            assertContains(result.stderr(), "Unsupported file extension", "CLI should explain why the input was rejected.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldResolveUnicodeNormalizedMacPaths() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-unicode");
        try {
            String displayFolderName = "숏폼";
            String actualFolderName = Normalizer.normalize(displayFolderName, Normalizer.Form.NFD);
            Path actualDirectory = Files.createDirectories(tempDir.resolve(actualFolderName));
            Files.writeString(actualDirectory.resolve("IMG_3001.HEIC"), "source");

            Path composedInput = tempDir.resolve(displayFolderName).resolve("IMG_3001.HEIC");
            Path composedOutputDir = tempDir.resolve(displayFolderName).resolve("converted");

            CliOptions options = new CliOptions(
                    List.of(composedInput),
                    composedOutputDir,
                    false,
                    true,
                    null,
                    false
            );

            ConversionPlan plan = new ConversionPlanner().plan(options);

            assertTrue(plan.errors().isEmpty(), "Unicode-normalized paths should resolve without validation errors.");
            assertEquals(1, plan.tasks().size(), "Planner should resolve the HEIC file under a normalized path.");
            assertEquals(
                    actualDirectory.resolve("IMG_3001.HEIC").toAbsolutePath().normalize(),
                    plan.tasks().get(0).source(),
                    "Planner should resolve the source to the actual filesystem path."
            );
            assertEquals(
                    actualDirectory.resolve("converted/IMG_3001.jpg").toAbsolutePath().normalize(),
                    plan.tasks().get(0).target(),
                    "Planner should resolve the output directory to the actual filesystem path."
            );
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static InvocationResult runCli(ImageConverter converter, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        HeicJpgCli cli = new HeicJpgCli(out, err, converter);
        int exitCode = cli.run(args);

        return new InvocationResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertContains(String actual, String expectedFragment, String message) {
        if (!actual.contains(expectedFragment)) {
            throw new AssertionError(message + " Actual output: " + actual);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private record InvocationResult(int exitCode, String stdout, String stderr) {
    }

    private static final class RecordingConverter implements ImageConverter {
        private final List<ConversionTask> calls = new ArrayList<>();

        @Override
        public void convert(ConversionTask task, Integer maxDimension) throws IOException {
            calls.add(task);
            Path parent = task.target().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(task.target(), "converted:" + maxDimension);
        }
    }
}
