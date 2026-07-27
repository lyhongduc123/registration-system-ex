package org.lhduc.registration.packet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.UUID;

@Getter
@Setter
public class ChallengePacket extends Packet {
    private UUID challengeId;
    private byte[] nonce;
    private Duration expiry;

    protected ChallengePacket(Header header,
                              UUID challengeId,
                              byte[] nonce,
                              Duration expiry) {
        super(header);
        this.challengeId = challengeId;
        this.nonce = nonce;
        this.expiry = expiry;
    }
}
