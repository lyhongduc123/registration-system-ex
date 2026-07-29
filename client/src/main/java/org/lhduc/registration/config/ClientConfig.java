package org.lhduc.registration.config;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
@Builder
public class ClientConfig {
    private final int serverPort;
    private final int clientNumber;
    private final int requestPerSecond;
    private final int renewBefore;
    private final int maxRetry;
    private final String secret;
    private final Duration leaseDuration;
}
