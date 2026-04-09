package io.github.jean202.heicjpg;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

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
              -h, --help                Show this help.

            Examples:
              heic-jpg ~/Pictures/IMG_0001.HEIC
              heic-jpg ~/Pictures/iPhone
              heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted
              heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted --max-dimension 2048
            """;

    private final PrintStream out;
    private final PrintStream err;
    private final ConversionPlanner planner;
    private final ImageConverter converter;

    HeicJpgCli(PrintStream out, PrintStream err, ImageConverter converter) {
        this(out, err, new ConversionPlanner(), converter);
    }

    HeicJpgCli(PrintStream out, PrintStream err, ConversionPlanner planner, ImageConverter converter) {
        this.out = out;
        this.err = err;
        this.planner = planner;
        this.converter = converter;
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

    private String formatMapping(ConversionTask task) {
        return task.source() + " -> " + task.target();
    }
}
