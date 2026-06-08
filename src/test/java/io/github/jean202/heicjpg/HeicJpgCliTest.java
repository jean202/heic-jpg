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
        shouldDeleteHeicWithMatchingJpgWhenConfirmed();
        shouldKeepHeicWithoutMatchingJpg();
        shouldKeepHeicWhenJpgIsEmpty();
        shouldNotDeleteWhenConfirmationDeclined();
        shouldMatchJpgUnderOutputDirStructure();
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
                    false,
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
                    false,
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

    private static void shouldDeleteHeicWithMatchingJpgWhenConfirmed() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-delete");
        try {
            Path source = tempDir.resolve("IMG_4001.HEIC");
            Path target = tempDir.resolve("IMG_4001.jpg");
            Files.writeString(source, "source");
            Files.writeString(target, "converted");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, new StubConfirmation(true),
                    "--delete-converted", source.toString());

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Confirmed cleanup should succeed.");
            assertTrue(!Files.exists(source), "Confirmed cleanup should delete the HEIC source.");
            assertTrue(Files.exists(target), "Cleanup must never delete the JPG target.");
            assertContains(result.stdout(), "Deleted 1 file(s)", "Cleanup should report the deleted count.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldKeepHeicWithoutMatchingJpg() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-keep");
        try {
            Path source = tempDir.resolve("IMG_4101.HEIC");
            Files.writeString(source, "source");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, new StubConfirmation(true),
                    "--delete-converted", source.toString());

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Cleanup with nothing to delete should succeed.");
            assertTrue(Files.exists(source), "HEIC without a matching JPG must be kept.");
            assertContains(result.stdout(), "KEEP", "Cleanup should report the kept file.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldKeepHeicWhenJpgIsEmpty() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-empty");
        try {
            Path source = tempDir.resolve("IMG_4201.HEIC");
            Path target = tempDir.resolve("IMG_4201.jpg");
            Files.writeString(source, "source");
            Files.writeString(target, "");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, new StubConfirmation(true),
                    "--delete-converted", source.toString());

            assertTrue(Files.exists(source), "HEIC must be kept when the matching JPG is empty.");
            assertContains(result.stdout(), "KEEP", "Empty JPG should leave the HEIC in the keep list.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldNotDeleteWhenConfirmationDeclined() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-decline");
        try {
            Path source = tempDir.resolve("IMG_4301.HEIC");
            Path target = tempDir.resolve("IMG_4301.jpg");
            Files.writeString(source, "source");
            Files.writeString(target, "converted");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, new StubConfirmation(false),
                    "--delete-converted", source.toString());

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Declining cleanup should still exit successfully.");
            assertTrue(Files.exists(source), "Declined cleanup must not delete anything.");
            assertContains(result.stdout(), "Aborted", "Declined cleanup should report the abort.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void shouldMatchJpgUnderOutputDirStructure() throws Exception {
        Path tempDir = Files.createTempDirectory("heic-jpg-outdir");
        try {
            Path sourceRoot = Files.createDirectories(tempDir.resolve("iphone"));
            Path source = sourceRoot.resolve("IMG_4401.HEIC");
            Files.writeString(source, "source");
            Path outputDir = tempDir.resolve("out");
            Path target = Files.createDirectories(outputDir.resolve("iphone")).resolve("IMG_4401.jpg");
            Files.writeString(target, "converted");

            RecordingConverter converter = new RecordingConverter();
            InvocationResult result = runCli(converter, new StubConfirmation(true),
                    "--output-dir", outputDir.toString(),
                    "--delete-converted", sourceRoot.toString());

            assertEquals(HeicJpgCli.EXIT_SUCCESS, result.exitCode(), "Cleanup with output-dir should succeed.");
            assertTrue(!Files.exists(source), "Cleanup should match the JPG under the preserved output-dir structure.");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static InvocationResult runCli(ImageConverter converter, String... args) {
        return runCli(converter, new StubConfirmation(false), args);
    }

    private static InvocationResult runCli(ImageConverter converter, UserConfirmation confirmation, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        HeicJpgCli cli = new HeicJpgCli(out, err, converter, confirmation);
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

    private static final class StubConfirmation implements UserConfirmation {
        private final boolean answer;

        private StubConfirmation(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean confirm(String message) {
            return answer;
        }
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
