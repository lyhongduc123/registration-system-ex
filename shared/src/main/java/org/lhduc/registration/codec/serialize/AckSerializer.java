package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.AckPacket;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AckSerializer implements PacketSerializer<AckPacket> {

    @Override
    public MessageType type() {
        return MessageType.ACK;
    }

    @Override
    public void write(OutputStream out, AckPacket packet) {
    }

    @Override
    public AckPacket read(InputStream in, PacketHeader header) {
        return new AckPacket(header);
    }
}
