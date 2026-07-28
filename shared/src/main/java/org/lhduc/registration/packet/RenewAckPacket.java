package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;
import org.lhduc.registration.protocol.StatusCode;

import java.time.Instant;

@Getter
@Setter
public class RenewAckPacket extends Packet {
    private StatusCode statusCode;
    private Instant newLeaseExpiry;

    public RenewAckPacket(PacketHeader header, StatusCode statusCode, Instant newLeaseExpiry) {
        super(header);
        this.statusCode = statusCode;
        this.newLeaseExpiry = newLeaseExpiry;
    }
}
