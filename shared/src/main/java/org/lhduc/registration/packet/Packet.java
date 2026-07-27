package org.lhduc.registration.packet;

import org.lhduc.registration.protocol.MessageType;

import java.time.Instant;
import java.util.UUID;

public abstract class Packet {
    private final Header header;

    protected Packet(Header header) {
        this.header = header;
    }

    public Header getHeader() {
        return header;
    }
}
