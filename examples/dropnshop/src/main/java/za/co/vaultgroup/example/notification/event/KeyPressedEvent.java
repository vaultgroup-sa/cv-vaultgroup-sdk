package za.co.vaultgroup.example.notification.event;

import lombok.Getter;

@Getter
public class KeyPressedEvent extends Event {
    private final char code;

    public static EventType eventTypeFromCode(char code) {
        if (code >= '0' && code <= '9') {
            return EventType.DIGIT_PRESSED;
        } else if (code == '#') {
            return EventType.ENTER_PRESSED;
        } else if (code == '*') {
            return EventType.RESET_PRESSED;
        }

        throw new IllegalArgumentException("Unexpected key pressed: " + (int) code);
    }

    public KeyPressedEvent(char code) {
        super(eventTypeFromCode(code));
        this.code = code;
    }
}
