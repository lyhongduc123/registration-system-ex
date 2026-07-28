package org.lhduc.registration.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ClientSession {
    private UUID clientId;
    private UUID sessionId;
    private SessionStatus status;
    private Instant registeredAt;
    private Instant expiredAt;
}
