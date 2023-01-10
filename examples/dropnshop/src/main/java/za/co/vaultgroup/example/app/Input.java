package za.co.vaultgroup.example.app;

import org.apache.commons.lang3.StringUtils;

/**
 * A simple class that represents a customer's input.
 * See also {@link Screen#setInputEcho(String)}.
 */
public class Input {
    // Whether an "echoed" input must be hidden under asterisk characters.
    private final boolean hidden;

    // A maximum number of characters expected as an input.
    private final int limit;

    // The characters that customer entered.
    private String text = "";

    public Input(boolean hidden, int limit) {
        this.hidden = hidden;
        this.limit = limit;
    }

    public String getText() {
        return text;
    }

    public String getEcho() {
        if (hidden) {
            return StringUtils.repeat('*', text.length());
        } else {
            return text;
        }
    }

    public boolean input(char code) {
        if (text.length() < limit) {
            text += code;
            return true;
        }

        return false;
    }

    public void clear() {
        text = "";
    }
}
