package io.github.jean202.heicjpg;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        HeicJpgCli cli = new HeicJpgCli(System.out, System.err, new SipsImageConverter());
        int exitCode = cli.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
