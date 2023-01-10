package za.co.vaultgroup.example.app;

import org.apache.commons.lang3.StringUtils;
import za.co.vaultgroup.example.Api;
import za.co.vaultgroup.example.config.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Screen {
    // It's an actual resolution of LCD screen: 20x4 characters.
    public static final int CHARACTERS_PER_LINE = 20; // How many characters per line we have.
    public static final int NUMBER_OF_LINES = 4; // How many lines we have.

    // Whitespace character obviously represents blank character position.
    private static final String EMPTY_LINE = StringUtils.repeat(' ', CHARACTERS_PER_LINE);

    // We use API to communicate with the actual hardware.
    private final Api api;

    // Initial state is a completely clear screen.
    private final List<String> screenRows = new ArrayList<>(Collections.nCopies(NUMBER_OF_LINES, EMPTY_LINE));

    // Page is an object that represents some message currently shown at the screen.
    private Page page;


    // Most of the time we have to "echo" a customer's input, so they can see what they entered.
    // If it's a password we still "echo" an input but all the characters are asterisks for security reasons.
    private String inputEcho = "";

    // To "echo" a customer's input we always use the next free line after "static" text message.
    private int inputEchoRow;

    public Screen(Api api) {
        this.api = api;
    }

    public void show(Page page, Object ...args) {
        this.page = page;

        List<String> lines = getLines(page, args);

        for (int row = 0; row < NUMBER_OF_LINES; row++) {
            if (row < lines.size()) {
                writeLine(row, true, lines.get(row));
            } else {
                writeLine(row, false, EMPTY_LINE);
            }
        }

        inputEchoRow = lines.size();
    }

    public boolean is(Page page) {
        return this.page == page;
    }

    public void setInputEcho(String text) {
        if (page != null) {
            // Just trim what doesn't fit the screen line.
            if (text.length() > CHARACTERS_PER_LINE) {
                text = text.substring(0, CHARACTERS_PER_LINE);
            }

            inputEcho = text;
            writeLine(inputEchoRow, true, inputEcho);
        }
    }

    public void clear() {
        page = null;
        Collections.fill(screenRows, EMPTY_LINE);
        inputEchoRow = 0;
        inputEcho = "";
        api.clearScreen();
    }

    private void writeLine(int row, boolean isCentered, String line) {
        if (StringUtils.isBlank(line)) {
            writeRow(row, EMPTY_LINE);
        } else if (isCentered) {
            writeRow(row, StringUtils.center(line.trim(), CHARACTERS_PER_LINE));
        } else {
            writeRow(row, StringUtils.rightPad(line, CHARACTERS_PER_LINE));
        }
    }

    private void writeRow(int row, String text) {
        if (row < 0 || row >= NUMBER_OF_LINES) {
            throw new IllegalArgumentException("An attempt to write LCD row #" + row);
        }

        if (text.length() != CHARACTERS_PER_LINE) {
            throw new IllegalArgumentException("An attempt to write " + text.length() + " character(s) instead of " + CHARACTERS_PER_LINE + " to LCD row");
        }

        // Don't waste time if text is already displayed.
        // Also, it would cause a not particularly nice blinking of the screen when you re-write its content.
        String previousLineText = screenRows.set(row, text);
        if (!previousLineText.equals(text)) {
            int refreshFirstPosition = -1;
            int refreshLastPosition = -1;

            // An LCD screen is rather a slow device, so it's better to make sure
            // we only rewrite characters that have really changes.
            for (int i = 0; i < CHARACTERS_PER_LINE; i++) {
                if (text.charAt(i) != previousLineText.charAt(i)) {
                    if (refreshFirstPosition < 0) {
                        refreshFirstPosition = i;
                    }
                    refreshLastPosition = i;
                }
            }

            api.writeScreen(row, refreshFirstPosition, text.substring(refreshFirstPosition, refreshLastPosition + 1));
        }
    }

    private List<String> getLines(Page page, Object ...args) {
        return toLines(String.format(page.getMessage(), args));
    }

    private List<String> toLines(String message) {
        List<String> lines = new ArrayList<>();

        for (String line : message.split("\n")) {
            while (line.length() > CHARACTERS_PER_LINE) {
                lines.add(line.substring(0, CHARACTERS_PER_LINE));
                line = line.substring(CHARACTERS_PER_LINE);
            }
            lines.add(line);
        }

        if (lines.size() > NUMBER_OF_LINES) {
            return lines.subList(0, NUMBER_OF_LINES);
        }

        return lines;
    }
}
