package za.co.vaultgroup.example.app;

public enum VaultState {
    /**
     * The initial state.
     * Vault gets initialized and then shows application name and version for several seconds.
     * Then moves to STANDBY state.
     */
    GREETING,

    /**
     * The state when a vault is waiting for a new customer to choose between dropoff and pickup.
     */
    STANDBY,

    /**
     * The state when a vault is displaying some message (success or error).
     * There always should be a timer, then vault gets to some other state (usually STANDBY).
     */
    ALERT,

    /**
     * The state when a customer requested dropoff and now has to enter a new password to be used.
     */
    DROPOFF_PASSWORD,

    /**
     * The state when a customer requested a dropoff and provided a password.
     * Now a locker is open and there's a 2 minutes timeout during which a customer is supposed to
     * put their belongings to the locker and close it.
     */
    DROPOFF_PENDING,

    /**
     * The state when a customer requested a pickup and now has to specify their locker.
     */
    PICKUP_CHOOSE_LOCKER,

    /**
     * The state when a customer requested a pickup, specified their locker and has to enter a password to unlock it.
     */
    PICKUP_PASSWORD,

    /**
     * The state when a customer confirmed a pickup and locker is open and waiting for customer.
     */
    PICKUP_PENDING
}
