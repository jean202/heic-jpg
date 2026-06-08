package io.github.jean202.heicjpg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ConsoleUserConfirmation implements UserConfirmation {
    private final InputStream in;
    private final PrintStream out;

    ConsoleUserConfirmation(InputStream in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public boolean confirm(String message) {
        out.print(message);
        out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line = reader.readLine();
            if (line == null) {
                return false;
            }
            String answer = line.trim().toLowerCase(Locale.ROOT);
            return answer.equals("y") || answer.equals("yes");
        } catch (IOException exception) {
            return false;
        }
    }
}
