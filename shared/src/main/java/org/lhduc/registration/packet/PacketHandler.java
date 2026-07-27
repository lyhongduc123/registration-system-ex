package org.lhduc.registration.packet;

import com.sun.jdi.connect.spi.Connection;
import org.lhduc.registration.protocol.MessageType;

public interface PacketHandler<T extends Packet> {
    MessageType type();
    void handle(Connection conn, T packet);
}
