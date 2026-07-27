package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.protocol.MessageType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface PacketSerializer<T extends Packet> {
    MessageType type();

    void write(OutputStream out, T packet) throws IOException;
    T read(InputStream in) throws IOException;
}
