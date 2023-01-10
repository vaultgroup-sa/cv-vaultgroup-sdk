package za.co.vaultgroup.example;

import lombok.extern.slf4j.Slf4j;
import za.co.vaultgroup.example.app.Vault;
import za.co.vaultgroup.example.config.Settings;

@Slf4j
public class Application {
    public static void main(String[] args) {
        log.info("Starting...");

        try {
            Settings settings = Settings.get();

            if (settings == null) {
                log.error("Exiting");
            } else {
                Vault vault = new Vault(settings);
                vault.run();

                log.info("Finished");
            }
        } catch (Exception e) {
            log.error("Failed", e);
        }
    }
}
