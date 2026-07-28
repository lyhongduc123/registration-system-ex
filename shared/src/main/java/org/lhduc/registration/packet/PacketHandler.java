package org.lhduc.registration.packet;

import org.lhduc.registration.network.Connection;
import org.lhduc.registration.protocol.MessageType;

public interface PacketHandler<T extends Packet> {
    MessageType type();
    void handle(Connection conn, T packet);
}
