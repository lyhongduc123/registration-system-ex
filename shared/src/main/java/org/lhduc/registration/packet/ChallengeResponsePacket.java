package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ChallengeResponsePacket extends Packet {
    private String clientId;
    private UUID challengeId;
    private byte[] responseHash;

    public ChallengeResponsePacket(PacketHeader header, String clientId, UUID challengeId, byte[] responseHash) {
        super(header);
        this.clientId = clientId;
        this.challengeId = challengeId;
        this.responseHash = responseHash;
    }
}
