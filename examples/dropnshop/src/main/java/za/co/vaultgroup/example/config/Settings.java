package za.co.vaultgroup.example.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;

@Slf4j
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private static final String FILENAME = "settings.yaml";

    private static final int PORT_MIN = 1024;
    private static final int PORT_MAX = 49151;

    @JsonProperty("notifications")
    private NotificationSettings notificationSettings;

    @JsonProperty("grpc-server")
    private String grpcServer;

    public static Settings get() {
        try {
            ClassLoader classLoader = Settings.class.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream(FILENAME);

            if (inputStream == null) {
                log.error("Cannot find {} resource file", FILENAME);
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            Settings settings = objectMapper.readValue(inputStream, Settings.class);

            if (validate(settings)) {
                return settings;
            } else {
                log.error("Settings are invalid");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to load application settings", e);
            return null;
        }
    }

    private static boolean validate(Settings settings) {
        if (StringUtils.isEmpty(settings.getGrpcServer())) {
            log.error("Invalid settings: missing required `grpc-server` property");
            return false;
        }

        NotificationSettings notificationSettings = settings.getNotificationSettings();

        if (notificationSettings == null) {
            log.error("Invalid settings: missing required `notifications.*` properties");
            return false;
        }

        if (notificationSettings.getPort() < PORT_MIN || notificationSettings.getPort() > PORT_MAX) {
            log.error("Invalid settings: `notifications.port` must be between {} and {}", PORT_MIN, PORT_MAX);
            return false;
        }

        return true;
    }

    @Getter
    @Setter
    public static class NotificationSettings {
        @JsonProperty("port")
        private int port;

        @JsonProperty("listen-remote")
        private boolean listenRemote = false;
    }
}
