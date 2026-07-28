package org.lhduc.registration.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Challenge {
    private UUID challengeId;
    private UUID clientId;

    private final byte[] nonce;
    private Instant expiredAt;
    private boolean isUsed;

    public Challenge(UUID challengeId, UUID clientId, byte[] nonce) {
        this.challengeId = challengeId;
        this.clientId = clientId;
        this.nonce = nonce;
    }

    @Builder
    public Challenge(UUID challengeId, UUID clientId, byte[] nonce, Instant expiredAt) {
        this.challengeId = challengeId;
        this.clientId = clientId;
        this.nonce = nonce;
        this.expiredAt = expiredAt;
    }
}
