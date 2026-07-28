package org.lhduc.registration.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

@Slf4j
public final class ConfigLoader {
    public static ServerConfig load() {
        Properties prop = new Properties();
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in == null) {
                throw new RuntimeException("Cannot load server.properties");
            }
            prop.load(in);
            ServerConfig serverConfig = ServerConfig.builder()
                    .port(parseInt(prop, "server.port"))
                    .maxRetry(parseInt(prop, "server.max-retry"))
                    .leaseDuration(require(prop, "lease.seconds", Duration.ofSeconds(60)))
                    .challengeTimeout(require(prop, "challenge.seconds", Duration.ofSeconds(30)))
                    .clientCount(parseInt(prop, "server.client-count"))
                    .secret(require(prop, "server.secret"))
                    .build();

            validate(serverConfig);
            return serverConfig;
        } catch (IOException exception) {
            log.error("Cannot load server.properties", exception);
            throw new RuntimeException(exception);
        }
    }

    private static String require(Properties prop, String key) {
        String value = prop.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }

        return value;
    }

    private static Duration require(Properties prop, String key, Duration defaultValue) {
        String value = prop.getProperty(key);
        if (value == null) {
            log.warn("Not found: {}. Using default value: {}", key, defaultValue);
            return defaultValue;
        }
        return Duration.ofSeconds(Integer.parseInt(value));
    }

    private static int parseInt(Properties prop, String key) {
        try {
            return Integer.parseInt(require(prop, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    private static void validate(ServerConfig config) {
        if (config.getPort() < 1 || config.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if  (config.getMaxRetry() < 1 || config.getMaxRetry() > 65535) {
            throw new IllegalArgumentException("Max retry must be between 1 and 65535");
        }
    }
}
