package org.lhduc.registration.config;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
@Builder
public class ServerConfig {
    private final int port;
    private final Duration leaseDuration;
    private final Duration challengeTimeout;
    private final int maxRetry;
    private final int clientCount;
    private final String secret;
}
