package org.lhduc.registration.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

@Slf4j
public final class ConfigLoader {
    public static ClientConfig load() {
        Properties prop = new Properties();
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (in == null) {
                throw new RuntimeException("Cannot load client.properties");
            }
            prop.load(in);
            ClientConfig config = ClientConfig.builder()
                    .serverPort(parseInt(prop, "server.port"))
                    .maxRetry(parseInt(prop, "client.max-retry"))
                    .secret(require(prop, "client.secret"))
                    .leaseDuration(require(prop, "lease.seconds", Duration.ofSeconds(60)))
                    .renewBefore(parseInt(prop, "client.time-left-to-renew"))
                    .clientNumber(parseInt(prop, "client.numbers"))
                    .requestPerSecond(parseInt(prop, "client.rps"))
                    .build();

            validate(config);
            return config;
        } catch (IOException exception) {
            log.error("Cannot load client.properties", exception);
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

    private static void validate(ClientConfig config) {
        if (config.getClientNumber() < 1) {
            throw new IllegalArgumentException("Client number must be at least 1");
        }
        if (config.getRenewBefore() < 1) {
            throw new IllegalArgumentException("Renew before must be a positive integer larger than 0");
        }
        if (config.getMaxRetry() < 1) {
            throw new IllegalArgumentException("Max retry must be at least 1");
        }
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }
}