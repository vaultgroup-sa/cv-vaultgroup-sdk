package za.co.vaultgroup.example.app;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import za.co.vaultgroup.example.Api;
import za.co.vaultgroup.example.config.Buzz;
import za.co.vaultgroup.example.config.Config;
import za.co.vaultgroup.example.config.LockerState;
import za.co.vaultgroup.example.config.Page;
import za.co.vaultgroup.example.config.Settings;
import za.co.vaultgroup.example.config.Settings.NotificationSettings;
import za.co.vaultgroup.example.config.Timing;
import za.co.vaultgroup.example.notification.NotificationServer;
import za.co.vaultgroup.example.notification.event.Event;
import za.co.vaultgroup.example.notification.event.EventType;
import za.co.vaultgroup.example.notification.event.KeyPressedEvent;
import za.co.vaultgroup.example.notification.event.LockerStateChangedEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
public class Vault {
    private static final String APPLICATION_VERSION = "1.0.0";

    /**
     * Locker number must be between 1 and 999.
     * See {@link Page#PICKUP_CHOOSE_LOCKER} and {@link VaultState#PICKUP_CHOOSE_LOCKER}.
     */
    private static final int LOCKER_NUMBER_DIGITS = 3;

    /**
     * See {@link Page#DROPOFF_PASSWORD}, {@link Page#PICKUP_ENTER_PASSWORD}, {@link VaultState#DROPOFF_PASSWORD}
     * * and {@link VaultState#PICKUP_PASSWORD}.
     */
    private static final int PASSWORD_DIGITS = 5;

    /**
     * Too simple to guess.
     */
    private static final List<String> SIMPLE_PASSWORDS = Arrays.asList("00000", "11111", "22222", "33333", "44444", "55555", "66666", "77777", "88888", "99999", "12345", "54321");

    /**
     * See {@link Page#STANDBY}.
     */
    private static final char DROPOFF_CHOICE_CODE = '1';

    /**
     * See {@link Page#STANDBY}.
     */
    private static final char PICKUP_CHOICE_CODE = '2';

    private final Settings settings;
    private final Api api;
    private final Screen screen;
    private final Config config = new Config();
    private VaultState state;
    private Input input;
    private Integer dropoffLockerId;
    private Integer pickupLockerId;

    // Key is a lockerId.
    // Null value means a locker is free to use, otherwise value is a password.
    private final Map<Integer, String> dropoffs = new HashMap<>();

    private Timer timer = new Timer();

    public Vault(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
        this.api = new Api(settings.getGrpcServer());
        this.screen = new Screen(api);
    }

    public void run() throws IOException {
        initialize();

        // Run notification server in main thread so all the incoming notifications are handled synchronously one at a time.
        // Anything that needs async execution (like handling of timeouts) is managed by timer.
        NotificationSettings notificationSettings = settings.getNotificationSettings();
        NotificationServer server = new NotificationServer(notificationSettings.getPort(), notificationSettings.isListenRemote(), this::handle);
        server.run();
    }

    private void initialize() {
        log.info("Initializing...");
        log.info("API version is {}", api.getVersion());

        // Get vault dimensions from API.
        Api.LockerMap lockerMap = api.getLockerMap();

        log.info("Vault has {} locker(s), dimensions are {}", lockerMap.getCount(), StringUtils.join(lockerMap.getMapping(), '-'));

        config.setLockersCount(lockerMap.getCount());
        config.setMapping(lockerMap.getMapping());

        // We always start with greeting message and never get back to it until vault reboot.
        state = VaultState.GREETING;

        // Everything seems to go well so far, let's indicate that on an LCD screen.
        // Make sure to clear the screen just in case (there could be some leftover text if application restarted without hardware reboot).
        screen.clear();
        screen.show(Page.GREETING, APPLICATION_VERSION);

        // Almost there, now we wait for the hardware to be initialized if needed.
        // Then we find out which lockers are locked and which aren't.
        initializeLockerStates(true);
    }

    private void initializeLockerStates(boolean isFirstTry) {
        List<LockerState> states = api.getLockerStates();

        if (states == null) {
            // Something is not initialized, wait for some time and try again.
            log.error("Lockers are not initialized yet");

            // Try again later until initialization is finally successful.
            defer(Timing.REINITIALIZATION, () -> initializeLockerStates(false));
        } else {
            log.info("Lockers are initialized");

            Runnable onInitializationFinished = () -> {
                // Iterate over all the lockers available.
                long unlockedCount = states.stream()
                        .filter(state -> state != LockerState.LOCKED)
                        .count();

                log.info("Unlocked lockers count: {}", unlockedCount);

                // Now we are waiting for customers, it's a standby state.
                screen.show(Page.STANDBY);
                api.buzz(Buzz.EVENT);
                state = VaultState.STANDBY;
            };

            // If initialization is successful at the first time (without extra attempts),
            // we use a timeout to give people few seconds to actually read that greeting message.
            if (isFirstTry) {
                defer(Timing.GREETING, onInitializationFinished);
            } else {
                onInitializationFinished.run();
            }
        }
    }

    private synchronized void handle(Event event) {
        if (event instanceof KeyPressedEvent) {
            System.out.println("Input: " + ((KeyPressedEvent) event).getCode());
        }

        switch (state) {
            case GREETING:
            case ALERT:
                // Ignore all input while greeting or alert message is shown.
                break;

            case STANDBY:
                handleInStandbyState(event);
                break;

            case DROPOFF_PASSWORD:
                handleInDropoffPasswordState(event);
                break;

            case DROPOFF_PENDING:
                handleInDropoffPendingState(event);
                break;

            case PICKUP_CHOOSE_LOCKER:
                handleInPickupChooseLockerState(event);
                break;

            case PICKUP_PASSWORD:
                handleInPickupPasswordState(event);
                break;

            case PICKUP_PENDING:
                handleInPickupPendingState(event);
                break;
        }
    }

    private void handleInStandbyState(Event event) {
        switch (event.getType()) {
            case DIGIT_PRESSED: {
                KeyPressedEvent ev = (KeyPressedEvent) event;

                switch (ev.getCode()) {
                    case DROPOFF_CHOICE_CODE: {
                        // Customer requested a dropoff.
                        dropoffLockerId = pickRandomLockerForDropoff();

                        if (dropoffLockerId == null) {
                            // Show error message, buzz with buzzer.
                            api.buzz(Buzz.ERROR);
                            screen.show(Page.DROPOFF_NO_FREE_LOCKERS);

                            // Now we in an alert state.
                            // Wait for some time (so customer has time to read message) and get back to standby state.
                            state = VaultState.ALERT;
                            defer(Timing.ALERT_SHORT, () -> {
                                state = VaultState.STANDBY;
                                screen.show(Page.STANDBY);
                            });
                        } else {
                            state = VaultState.DROPOFF_PASSWORD;
                            api.buzz(Buzz.EVENT);
                            screen.show(Page.DROPOFF_PASSWORD);

                            // Prepare for password input.
                            input = new Input(true, PASSWORD_DIGITS);
                        }
                    }
                    break;

                    case PICKUP_CHOICE_CODE: {
                        // Customer requested a pickup.
                        state = VaultState.PICKUP_CHOOSE_LOCKER;
                        api.buzz(Buzz.EVENT);
                        screen.show(Page.PICKUP_CHOOSE_LOCKER);

                        // Prepare for locker number input.
                        input = new Input(false, LOCKER_NUMBER_DIGITS);
                    }
                    break;

                    default:
                        // Just indicate an unexpected input using a buzzer.
                        api.buzz(Buzz.ERROR);
                        break;
                }
            }
            break;

            case ENTER_PRESSED:
            case RESET_PRESSED:
                // Just indicate an unexpected input using a buzzer.
                api.buzz(Buzz.ERROR);
                break;
        }
    }

    private Integer pickRandomLockerForDropoff() {
        List<Integer> freeLockers = new ArrayList<>();

        // Collect all free lockers first.
        for (int lockerId = 1; lockerId < config.getLockersCount(); lockerId++) {
            if (dropoffs.get(lockerId) == null) {
                freeLockers.add(lockerId);
            }
        }

        // Then pick a random one (if available) to guarantee even utilization.
        if (freeLockers.size() > 0) {
            return freeLockers.get(RandomUtils.nextInt(0, freeLockers.size()));
        }

        // No free lockers available.
        return null;
    }

    private void handleInDropoffPasswordState(Event event) {
        switch (event.getType()) {
            case ENTER_PRESSED: {
                String password = input.getText();

                // Validate password length.
                if (password.length() < PASSWORD_DIGITS) {
                    // Show error message, buzz with buzzer.
                    input.clear();
                    api.buzz(Buzz.ERROR);
                    screen.show(Page.DROPOFF_PASSWORD_TOO_SHORT);

                    // Now we in an alert state.
                    // Wait for some time (so customer has time to read message) and get let them enter another password.
                    state = VaultState.ALERT;
                    defer(Timing.ALERT_SHORT, () -> {
                        state = VaultState.DROPOFF_PASSWORD;
                        screen.show(Page.DROPOFF_PASSWORD);
                    });
                } else if (isPasswordTooSimple(password)) {
                    // Show error message, buzz with buzzer.
                    input.clear();
                    api.buzz(Buzz.ERROR);
                    screen.show(Page.DROPOFF_PASSWORD_TOO_SIMPLE);

                    // Now we in an alert state.
                    // Wait for some time (so customer has time to read message) and get let them enter another password.
                    state = VaultState.ALERT;
                    defer(Timing.ALERT_SHORT, () -> {
                        state = VaultState.DROPOFF_PASSWORD;
                        screen.show(Page.DROPOFF_PASSWORD);
                    });
                } else {
                    // Success, now let's prompt a customer to put their belongings to the locker.
                    input = null;
                    api.buzz(Buzz.EVENT);

                    // Make sure locker is unlocked so a customer can actually access it.
                    api.setLockState(dropoffLockerId, false);
                    state = VaultState.DROPOFF_PENDING;
                    screen.show(Page.DROPOFF, dropoffLockerId);

                    // Remember password for this locker/dropoff.
                    dropoffs.put(dropoffLockerId, password);

                    defer(Timing.DROPOFF_PENDING, () -> {
                        // Dropoff failed!
                        dropoffs.remove(dropoffLockerId);
                        dropoffLockerId = null;

                        // Indicate explicitly that something went completely wrong.
                        api.buzz(Buzz.ANNOYING);

                        // Now we in an alert state.
                        // Wait for longer time (so customer has time to read message for sure) and get back to STANDBY state.
                        state = VaultState.ALERT;
                        screen.show(Page.DROPOFF_TIMEOUT);
                        defer(Timing.ALERT_LONG, () -> {
                            state = VaultState.STANDBY;
                            screen.show(Page.STANDBY);
                        });
                    });
                }
            }
            break;

            case RESET_PRESSED: {
                // Clear input by customer's request.
                input.clear();
                screen.setInputEcho("");
                api.buzz(Buzz.EVENT);
            }
            break;

            case DIGIT_PRESSED: {
                KeyPressedEvent ev = (KeyPressedEvent) event;

                if (input.input(ev.getCode())) {
                    screen.setInputEcho(input.getEcho());
                } else {
                    // Password's too long, ignore input and indicate error with buzzer.
                    api.buzz(Buzz.ERROR);
                }
            }
            break;
        }
    }

    private void handleInDropoffPendingState(Event event) {
        switch (event.getType()) {
            case DOOR_CLOSED: {
                LockerStateChangedEvent ev = (LockerStateChangedEvent) event;

                // Make sure a customer closed the right locker door.
                if (ev.getLockerId() == dropoffLockerId) {
                    // Once door is closed trigger the locking mechanism.
                    api.setLockState(dropoffLockerId, true);

                    // Double check to make sure the door is closed and locking mechanism is engaged.
                    // Make sure to convert 1-based lockerId to its 0-based index in a list.
                    LockerState lockerState = api.getLockerStates().get(dropoffLockerId - 1);

                    if (lockerState == LockerState.LOCKED) {
                        // Success!
                        // Cancel previous 2 minutes timeout.
                        cancelDeferred();

                        // Indicate success with a buzzer.
                        api.buzz(Buzz.EVENT);

                        // Now we in an alert state.
                        // Wait for some time (so customer has time to read message) and get back to STANDBY state.
                        state = VaultState.ALERT;
                        screen.show(Page.DROPOFF_SUCCESS);
                        defer(Timing.ALERT_SHORT, () -> {
                            state = VaultState.STANDBY;
                            screen.show(Page.STANDBY);
                        });
                    } else {
                        // Indicate explicitly that something went completely wrong.
                        api.buzz(Buzz.ANNOYING);

                        // Trigger unlocking to make 100% sure that a customer won't end up with their belongings in a locker without access to it.
                        api.setLockState(dropoffLockerId, false);
                        dropoffs.remove(dropoffLockerId);

                        // Now we in an alert state.
                        // Wait for longer time (so customer has time to read message for sure) and get back to STANDBY state.
                        state = VaultState.ALERT;
                        screen.show(Page.DROPOFF_CANCELLED);
                        defer(Timing.ALERT_LONG, () -> {
                            state = VaultState.STANDBY;
                            screen.show(Page.STANDBY);
                        });
                    }

                    dropoffLockerId = null;
                }
            }
            break;

            case RESET_PRESSED: {
                // Indicate explicitly that dropoff is cancelled.
                api.buzz(Buzz.ANNOYING);
                dropoffs.remove(dropoffLockerId);
                dropoffLockerId = null;

                // Now we in an alert state.
                // Wait for longer time (so customer has time to read message for sure) and get back to STANDBY state.
                state = VaultState.ALERT;
                screen.show(Page.DROPOFF_CANCELLED);
                defer(Timing.ALERT_LONG, () -> {
                    state = VaultState.STANDBY;
                    screen.show(Page.STANDBY);
                });
            }
            break;

            case DIGIT_PRESSED:
            case ENTER_PRESSED:
                // Just indicate an unexpected input using a buzzer.
                api.buzz(Buzz.ERROR);
                break;
        }
    }

    private void handleInPickupChooseLockerState(Event event) {
        switch (event.getType()) {
            case DIGIT_PRESSED: {
                KeyPressedEvent ev = (KeyPressedEvent) event;

                if (input.input(ev.getCode())) {
                    screen.setInputEcho(input.getEcho());
                } else {
                    // Locker number is too long, ignore input and indicate error with buzzer.
                    api.buzz(Buzz.ERROR);
                }
            }
            break;

            case ENTER_PRESSED: {
                int lockerId = NumberUtils.toInt(input.getText());

                if (validateLockerId(lockerId) && dropoffs.get(lockerId) != null) {
                    log.info("Selected locker #{}", lockerId);
                    pickupLockerId = lockerId;
                    input.clear();

                    // Next step is to prompt for a password.
                    state = VaultState.PICKUP_PASSWORD;
                    screen.show(Page.PICKUP_ENTER_PASSWORD);
                    api.buzz(Buzz.EVENT);
                    input = new Input(true, PASSWORD_DIGITS);
                } else {
                    log.info("Selected invalid locker that doesn't exist");
                    screen.show(Page.PICKUP_LOCKER_INVALID);
                    api.buzz(Buzz.ERROR);
                    state = VaultState.ALERT;
                    defer(Timing.ALERT_SHORT, () -> {
                        state = VaultState.PICKUP_CHOOSE_LOCKER;
                        screen.show(Page.PICKUP_CHOOSE_LOCKER);
                        input.clear();
                    });
                }
            }
            break;

            case RESET_PRESSED: {
                // Clear input by customer's request.
                input.clear();
                screen.setInputEcho("");
                api.buzz(Buzz.EVENT);
            }
            break;
        }
    }

    private void handleInPickupPasswordState(Event event) {
        switch (event.getType()) {
            case ENTER_PRESSED: {
                String password = input.getText();

                // Verify password.
                if (password.equals(dropoffs.get(pickupLockerId))) {
                    // Success, now let's unlock a locker and give the customer some time to open it and pick up their belongings.
                    input = null;
                    api.buzz(Buzz.EVENT);

                    // Trigger unlocking and then wait until the customer grabs their belongings (actually we will only wait for a locker door opening event).
                    api.setLockState(pickupLockerId, false);
                    state = VaultState.PICKUP_PENDING;
                    screen.show(Page.PICKUP, pickupLockerId);

                    defer(Timing.PICKUP_PENDING, () -> {
                        // Pickup failed!
                        // Lock a locker back to make sure nobody else can steal belongings from the locker.
                        api.setLockState(pickupLockerId, true);
                        state = VaultState.ALERT;
                        screen.show(Page.PICKUP_TIMEOUT);

                        // Indicate explicitly that something went completely wrong.
                        api.buzz(Buzz.ANNOYING);
                        defer(Timing.ALERT_LONG, () -> {
                            state = VaultState.STANDBY;
                            screen.show(Page.STANDBY);
                        });

                        pickupLockerId = null;
                    });
                } else {
                    // Password is invalid, try again.
                    api.buzz(Buzz.ERROR);
                    input.clear();
                    screen.show(Page.PICKUP_PASSWORD_INVALID);
                    state = VaultState.ALERT;

                    // Wait a little and then get back to password prompt screen (try again in 3 seconds).
                    defer(Timing.ALERT_SHORT, () -> {
                        screen.show(Page.PICKUP_ENTER_PASSWORD);
                        state = VaultState.PICKUP_PASSWORD;
                    });
                }
            }
            break;

            case RESET_PRESSED: {
                // Clear input by customer's request.
                input.clear();
                screen.setInputEcho("");
                api.buzz(Buzz.EVENT);
            }
            break;

            case DIGIT_PRESSED: {
                KeyPressedEvent ev = (KeyPressedEvent) event;

                if (input.input(ev.getCode())) {
                    screen.setInputEcho(input.getEcho());
                } else {
                    // Password's too long, ignore input and indicate error with buzzer.
                    api.buzz(Buzz.ERROR);
                }
            }
            break;
        }
    }

    private void handleInPickupPendingState(Event event) {
        if (event.getType() == EventType.DOOR_OPENED) {
            LockerStateChangedEvent ev = (LockerStateChangedEvent) event;

            // Make sure a customer opened the right locker door.
            if (ev.getLockerId() == pickupLockerId) {
                // Success!
                // Cancel previous 2 minutes timeout.
                cancelDeferred();

                // Mark the locker as available again.
                dropoffs.remove(pickupLockerId);

                // Indicate success with a buzzer.
                api.buzz(Buzz.EVENT);
                screen.show(Page.PICKUP_SUCCESS);
                state = VaultState.ALERT;

                defer(Timing.ALERT_SHORT, () -> {
                    state = VaultState.STANDBY;
                    screen.show(Page.STANDBY);
                });
            }
        }
    }

    private boolean isPasswordTooSimple(String password) {
        for (String simplePassword : SIMPLE_PASSWORDS) {
            if (simplePassword.equals(password)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateLockerId(int lockerId) {
        return lockerId >= 1 && lockerId <= config.getLockersCount();
    }

    private synchronized void defer(Timing timeoutValue, Runnable task) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timer = new Timer();
                synchronized (Vault.this) {
                    task.run();
                }
            }
        }, timeoutValue.toMilliseconds());
    }

    private synchronized void cancelDeferred() {
        try {
            timer.cancel();
            timer = new Timer();
        } catch (IllegalStateException e) {
            // Do nothing.
        }
    }
}
