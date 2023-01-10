package za.co.vaultgroup.example.notification.event;

import lombok.Getter;

import java.util.Objects;

@Getter
public class Event {
    private final EventType type;

    public Event(EventType type) {
        this.type = Objects.requireNonNull(type);
    }
}
