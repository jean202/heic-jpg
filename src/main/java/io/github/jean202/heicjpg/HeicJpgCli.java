package io.github.jean202.heicjpg;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class HeicJpgCli {
    static final int EXIT_SUCCESS = 0;
    static final int EXIT_USAGE = 1;
    static final int EXIT_CONVERSION_FAILURE = 2;

    private static final String USAGE = """
            Usage:
              heic-jpg [options] <file-or-directory>...

            Options:
              -o, --output-dir <dir>    Write converted files under <dir>.
                                        File inputs go directly under <dir>.
                                        Directory inputs preserve the input root name.
                  --overwrite           Replace existing .jpg targets.
                  --dry-run             Print the planned work without converting files.
                  --max-dimension N     Resize the longest edge to N pixels before saving.
                  --delete-converted    Permanently delete HEIC/HEIF inputs that already
                                        have a matching .jpg. Prompts before deleting.
                  --rename-different    If a .jpg with the target name already exists but
                                        shows a different picture, write the conversion as
                                        name-1.jpg, name-2.jpg, ... An existing .jpg showing
                                        the same picture is left untouched.
              -h, --help                Show this help.

            Examples:
              heic-jpg ~/Pictures/IMG_0001.HEIC
              heic-jpg ~/Pictures/iPhone
              heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted
              heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted --max-dimension 2048
              heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted --delete-converted
              heic-jpg ~/Pictures/iPhone --rename-different
            """;

    private final PrintStream out;
    private final PrintStream err;
    private final ConversionPlanner planner;
    private final ImageConverter converter;
    private final UserConfirmation confirmation;

    HeicJpgCli(PrintStream out, PrintStream err, ImageConverter converter) {
        this(out, err, new ConversionPlanner(), converter, new ConsoleUserConfirmation(System.in, out));
    }

    HeicJpgCli(PrintStream out, PrintStream err, ConversionPlanner planner, ImageConverter converter) {
        this(out, err, planner, converter, new ConsoleUserConfirmation(System.in, out));
    }

    HeicJpgCli(PrintStream out, PrintStream err, ImageConverter converter, UserConfirmation confirmation) {
        this(out, err, new ConversionPlanner(), converter, confirmation);
    }

    HeicJpgCli(
            PrintStream out,
            PrintStream err,
            ConversionPlanner planner,
            ImageConverter converter,
            UserConfirmation confirmation
    ) {
        this.out = out;
        this.err = err;
        this.planner = planner;
        this.converter = converter;
        this.confirmation = confirmation;
    }

    int run(String[] args) {
        try {
            CliOptions options = CliOptionsParser.parse(args);
            if (options.help()) {
                out.print(USAGE);
                return EXIT_SUCCESS;
            }

            ConversionPlan plan = planner.plan(options);
            if (!plan.errors().isEmpty()) {
                for (String error : plan.errors()) {
                    err.println("error: " + error);
                }
                err.println();
                err.print(USAGE);
                return EXIT_USAGE;
            }

            if (plan.tasks().isEmpty()) {
                out.println("No HEIC/HEIF files found.");
                return EXIT_SUCCESS;
            }

            if (options.deleteConverted()) {
                return executeCleanup(plan);
            }

            if (options.renameDifferent() && !options.dryRun()) {
                return executeRenameDifferent(plan, options);
            }

            return executePlan(plan, options);
        } catch (IllegalArgumentException exception) {
            err.println("error: " + exception.getMessage());
            err.println();
            err.print(USAGE);
            return EXIT_USAGE;
        } catch (IOException exception) {
            err.println("error: " + exception.getMessage());
            return EXIT_CONVERSION_FAILURE;
        }
    }

    private int executePlan(ConversionPlan plan, CliOptions options) {
        int converted = 0;
        int skipped = 0;
        int planned = 0;
        int failed = 0;

        for (ConversionTask task : plan.tasks()) {
            if (Files.exists(task.target()) && !options.overwrite()) {
                out.println("SKIP " + formatMapping(task) + " (target exists)");
                skipped++;
                continue;
            }

            if (options.dryRun()) {
                out.println("PLAN " + formatMapping(task));
                planned++;
                continue;
            }

            try {
                converter.convert(task, options.maxDimension());
                out.println("OK   " + formatMapping(task));
                converted++;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                err.println("FAIL " + formatMapping(task));
                err.println("  Conversion interrupted.");
                failed++;
                break;
            } catch (IOException exception) {
                err.println("FAIL " + formatMapping(task));
                err.println("  " + exception.getMessage());
                failed++;
            }
        }

        if (options.dryRun()) {
            out.printf("Planned %d conversion(s); %d existing target(s) would be skipped.%n", planned, skipped);
            return EXIT_SUCCESS;
        }

        out.printf("Converted %d file(s); skipped %d; failed %d.%n", converted, skipped, failed);
        return failed == 0 ? EXIT_SUCCESS : EXIT_CONVERSION_FAILURE;
    }

    private int executeRenameDifferent(ConversionPlan plan, CliOptions options) {
        ContentAwareConverter contentConverter = new ContentAwareConverter(converter);
        int written = 0;
        int skipped = 0;
        int failed = 0;

        for (ConversionTask task : plan.tasks()) {
            try {
                ContentAwareConverter.Result result = contentConverter.convert(task, options.maxDimension());
                if (result.skipped()) {
                    out.println("SAME " + task.source() + "  (matches " + result.skippedExisting() + ")");
                    skipped++;
                } else {
                    out.println("OK   " + task.source() + " -> " + result.written());
                    written++;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                err.println("FAIL " + task.source());
                err.println("  Conversion interrupted.");
                failed++;
                break;
            } catch (IOException exception) {
                err.println("FAIL " + task.source());
                err.println("  " + exception.getMessage());
                failed++;
            }
        }

        out.printf("Wrote %d file(s); skipped %d (same picture); failed %d.%n", written, skipped, failed);
        return failed == 0 ? EXIT_SUCCESS : EXIT_CONVERSION_FAILURE;
    }

    private int executeCleanup(ConversionPlan plan) {
        List<ConversionTask> deletable = new ArrayList<>();
        int kept = 0;

        for (ConversionTask task : plan.tasks()) {
            if (hasConvertedJpg(task.target())) {
                out.println("DELETE  " + task.source() + "  (jpg: " + task.target() + ")");
                deletable.add(task);
            } else {
                out.println("KEEP    " + task.source() + "  (no matching .jpg)");
                kept++;
            }
        }

        out.printf("%d HEIC file(s) scanned: %d to delete, %d kept (no jpg).%n",
                plan.tasks().size(), deletable.size(), kept);

        if (deletable.isEmpty()) {
            out.println("Nothing to delete.");
            return EXIT_SUCCESS;
        }

        boolean confirmed = confirmation.confirm(
                String.format("Permanently delete %d file(s)? This cannot be undone. [y/N]: ", deletable.size()));
        if (!confirmed) {
            out.println("Aborted. No files deleted.");
            return EXIT_SUCCESS;
        }

        int deleted = 0;
        int failed = 0;
        for (ConversionTask task : deletable) {
            try {
                Files.delete(task.source());
                out.println("DELETED " + task.source());
                deleted++;
            } catch (IOException exception) {
                err.println("FAIL    " + task.source());
                err.println("  " + exception.getMessage());
                failed++;
            }
        }

        out.printf("Deleted %d file(s); failed %d.%n", deleted, failed);
        return failed == 0 ? EXIT_SUCCESS : EXIT_CONVERSION_FAILURE;
    }

    private boolean hasConvertedJpg(Path target) {
        try {
            return Files.isRegularFile(target) && Files.size(target) > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    private String formatMapping(ConversionTask task) {
        return task.source() + " -> " + task.target();
    }
}
