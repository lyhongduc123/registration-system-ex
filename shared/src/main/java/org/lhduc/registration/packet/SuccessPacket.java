package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;
import org.lhduc.registration.protocol.StatusCode;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class SuccessPacket extends Packet {
    private StatusCode statusCode;
    private UUID sessionId;
    private Instant leaseExpiry;

    public SuccessPacket(PacketHeader header, StatusCode statusCode, UUID sessionId, Instant leaseExpiry) {
        super(header);
        this.statusCode = statusCode;
        this.sessionId = sessionId;
        this.leaseExpiry = leaseExpiry;
    }
}
