package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterPacket extends Packet {
    private String clientId;
    private byte[] authHash;

    public RegisterPacket(PacketHeader header, String clientId, byte[] authHash) {
        super(header);
        this.clientId = clientId;
        this.authHash = authHash;
    }
}
