package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RenewPacket extends Packet {
    private String clientId;
    private UUID sessionId;

    public RenewPacket(PacketHeader header, String clientId, UUID sessionId) {
        super(header);
        this.clientId = clientId;
        this.sessionId = sessionId;
    }
}
