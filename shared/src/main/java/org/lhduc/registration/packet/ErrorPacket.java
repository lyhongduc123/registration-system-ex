package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;
import org.lhduc.registration.protocol.StatusCode;

@Getter
@Setter
public class ErrorPacket extends Packet {
    private StatusCode statusCode;
    private String message;

    public ErrorPacket(PacketHeader header, StatusCode statusCode, String message) {
        super(header);
        this.statusCode = statusCode;
        this.message = message;
    }
}
