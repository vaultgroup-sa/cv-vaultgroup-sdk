package za.co.vaultgroup.example.notification.event;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;

@Getter
public class LockerStateChangedEvent extends Event {
    private final int lockerId;
    private final Pair<Integer, Integer> offset;

    public LockerStateChangedEvent(EventType type, int lockerId, Pair<Integer, Integer> offset) {
        super(type);

        switch (type) {
            case DOOR_OPENED:
            case DOOR_CLOSED:
            case DOOR_LOCKED:
            case DOOR_UNLOCKED:
                this.lockerId = lockerId;
                this.offset = Objects.requireNonNull(offset);
                break;

            default:
                throw new IllegalArgumentException("Unexpected event type");
        }
    }
}
