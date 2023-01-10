package za.co.vaultgroup.example.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import za.co.vaultgroup.example.notification.event.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A UDP server that is listening for asynchronous notifications from a hardware.
 */
@Slf4j
public class NotificationServer {
    private static final String TYPE_KEY = "key";
    private static final String TYPE_DOOR_OPENED = "door_opened";
    private static final String TYPE_DOOR_CLOSED = "door_closed";
    private static final String TYPE_DOOR_LOCKED = "door_locked";
    private static final String TYPE_DOOR_UNLOCKED = "door_unlocked";

    // A locker position is described by two numbers: 0-based column (aka slave board) number and 1-based locker number within that column/slave.
    private static final Pattern LOCKER_OFFSET_PATTERN = Pattern.compile("\\[(\\d+):(\\d+)]");

    // Buffer must be big enough to fit any possible notification JSON message.
    private static final int BUFFER_SIZE = 32768;

    // A server port to listen.
    private final int port;

    // Whether remote connections allowed (could be useful for testing; for production it must be always false).
    private final boolean listenRemote;

    private final Consumer<Event> handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationServer(int port, boolean listenRemote, Consumer<Event> handler) {
        this.port = port;
        this.listenRemote = listenRemote;
        this.handler = handler;
    }

    public void run() throws IOException {
        DatagramSocket socket = createSocket();
        byte[] buffer = new byte[BUFFER_SIZE];

        log.info("Started listening udp:{} for notifications", port);

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            try {
                handle(objectMapper.readValue(packet.getData(), packet.getOffset(), packet.getLength(), Notification.class));
            } catch (IOException e) {
                log.error("Failed to parse incoming notification: {}", new String(packet.getData(), packet.getOffset(), packet.getLength()));
            }
        }
    }

    private DatagramSocket createSocket() throws SocketException {
        if (listenRemote) {
            return new DatagramSocket(port);
        } else {
            return new DatagramSocket(port, InetAddress.getLoopbackAddress());
        }
    }

    private void handle(Notification notification) {
        String type = notification.getType();
        List<KeyValue> values = notification.getValues();

        if (StringUtils.isNotEmpty(type) && CollectionUtils.isNotEmpty(values)) {
            switch (type) {
                case TYPE_KEY:
                    handleKeyNotification(values);
                    break;

                case TYPE_DOOR_OPENED:
                    handleDoorNotification(EventType.DOOR_OPENED, values);
                    break;

                case TYPE_DOOR_CLOSED:
                    handleDoorNotification(EventType.DOOR_CLOSED, values);
                    break;

                case TYPE_DOOR_LOCKED:
                    handleDoorNotification(EventType.DOOR_LOCKED, values);
                    break;

                case TYPE_DOOR_UNLOCKED:
                    handleDoorNotification(EventType.DOOR_UNLOCKED, values);
                    break;

                default:
                    log.error("Unknown notification type: {}", type);
                    break;
            }
        }
    }

    private void handleKeyNotification(List<KeyValue> values) {
        Optional<String> optKeyCode = values.stream()
                .filter(kv -> "value".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst();

        if (optKeyCode.isPresent()) {
            try {
                int code = Integer.parseInt(optKeyCode.get());
                handle(new KeyPressedEvent((char) code));
            } catch (IllegalArgumentException e) {
                log.error("Invalid key notification: invalid key code value");
            }
        } else {
            log.error("Invalid key notification: missing key code value");
        }
    }

    private void handleDoorNotification(EventType eventType, List<KeyValue> values) {
        Optional<String> optLockerId = values.stream()
                .filter(kv -> "locker".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst();

        Optional<String> optOffset = values.stream()
                .filter(kv -> "offset".equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst();

        if (optLockerId.isPresent() && optOffset.isPresent()) {
            try {
                int lockerId = Integer.parseInt(optLockerId.get());
                Pair<Integer, Integer> offset = parseLockerOffset(optOffset.get());
                handle(new LockerStateChangedEvent(eventType, lockerId, offset));
            } catch (IllegalArgumentException e) {
                log.error("Invalid locker state change notification: invalid locker ordinal or offset");
            }
        } else {
            log.error("Invalid locker state change notification: missing locker ordinal or offset");
        }
    }

    private Pair<Integer, Integer> parseLockerOffset(String string) {
        Matcher matcher = LOCKER_OFFSET_PATTERN.matcher(string);

        if (matcher.matches()) {
            int slave = Integer.parseInt(matcher.group(1));
            int locker = Integer.parseInt(matcher.group(2));

            if (slave < 0 || locker < 1) {
                throw new IllegalArgumentException();
            }

            return Pair.of(slave, locker);
        }

        throw new IllegalArgumentException();
    }

    private void handle(Event event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("Failed to handle event of type " + event.getType(), e);
        }
    }

    @Getter
    @Setter
    private static class Notification {
        @JsonProperty("type")
        private String type;

        @JsonProperty("vals")
        private List<KeyValue> values;
    }

    @Getter
    @Setter
    private static class KeyValue {
        @JsonProperty("k")
        private String key;

        @JsonProperty("v")
        private String value;
    }
}
