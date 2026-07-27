package org.lhduc.registration.packet;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class RegisterPacket extends Packet {
    private int port;
    private String credential;
    private String host;
    private String group;

    public RegisterPacket(
            Header header,
            int port,
            String credential,
            String host,
            String group
    ) {
        super(header);

        this.port = port;
        this.credential = credential;
        this.host = host;
        this.group = group;
    }
}
