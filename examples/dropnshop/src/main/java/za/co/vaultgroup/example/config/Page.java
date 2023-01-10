package za.co.vaultgroup.example.config;

/**
 * A class that represents a text message displayed on an LCD screen (see {@link za.co.vaultgroup.example.app.Screen}).
 */
public enum Page {
    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#GREETING}
     */
    GREETING("Drop'n'shop v%s"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#STANDBY}
     */
    STANDBY("Press number\nto make a choice\n1 to dropoff\n2 to pickup"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#STANDBY}
     * and {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PASSWORD}
     */
    DROPOFF_NO_FREE_LOCKERS("Sorry,\nthere are no\nfree lockers"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PASSWORD}
     */
    DROPOFF_PASSWORD("Please choose your\n5-digit password"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PASSWORD}
     */
    DROPOFF_PASSWORD_TOO_SIMPLE("Your password is\ntoo simple.\nPlease,\nchoose another one"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PASSWORD}
     */
    DROPOFF_PASSWORD_TOO_SHORT("Your password is\ntoo short.\nPlease,\nuse exactly 5 digits"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PENDING}
     */
    DROPOFF("Please drop\nyour belongings\nto locker #%d\nand close the locker"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PENDING}
     * and {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    DROPOFF_SUCCESS("Thank you!\nHave a good shopping"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#DROPOFF_PENDING}
     * and {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    DROPOFF_CANCELLED("Dropoff cancelled"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    DROPOFF_TIMEOUT("You didn't close\nthe locker!\nTime is out!"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_CHOOSE_LOCKER}
     */
    PICKUP_CHOOSE_LOCKER("Enter locker No."),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_CHOOSE_LOCKER}
     * and {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    PICKUP_LOCKER_INVALID("Invalid locker No."),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_PASSWORD}
     */
    PICKUP_ENTER_PASSWORD("Enter you password"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_PASSWORD}
     * and {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    PICKUP_PASSWORD_INVALID("Password is invalid"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_PENDING}
     */
    PICKUP("Locker is opening...\nPlease pick up\nyour belongings\nfrom locker #%d"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    PICKUP_TIMEOUT("You didn't open\nthe locker!\nTime is out!"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#PICKUP_PENDING}
     */
    PICKUP_SUCCESS("Thank you!\nHave a nice day!"),

    /**
     * See {@link za.co.vaultgroup.example.app.VaultState#ALERT}
     */
    ERROR("Error occurred!");

    /**
     * To give it a nice consistent look make sure to put newline characters where you expect a text wrapping,
     * otherwise it'll be a hard wrapping which it's particularly nice. :-)
     */
    private final String message;

    Page(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
