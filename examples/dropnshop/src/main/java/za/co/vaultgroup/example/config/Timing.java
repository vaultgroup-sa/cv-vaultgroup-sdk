package za.co.vaultgroup.example.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public enum Timing {
    REINITIALIZATION(TimeUnit.SECONDS, 3),
    GREETING(TimeUnit.SECONDS, 3),
    DROPOFF_PENDING(TimeUnit.MINUTES, 2),
    PICKUP_PENDING(TimeUnit.MINUTES, 2),
    ALERT_SHORT(TimeUnit.SECONDS, 3),
    ALERT_LONG(TimeUnit.SECONDS, 5);

    private final TimeUnit timeUnit;
    private final long value;

    Timing(TimeUnit timeUnit, long value) {
        this.timeUnit = timeUnit;
        this.value = value;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long getValue() {
        return value;
    }

    public long toMilliseconds() {
        return timeUnit.toMillis(value);
    }
}
