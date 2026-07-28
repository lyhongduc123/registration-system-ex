package org.lhduc.registration.packet;

public abstract class Packet {
    private final PacketHeader header;

    protected Packet(PacketHeader header) {
        this.header = header;
    }

    public PacketHeader getHeader() {
        return header;
    }
}
