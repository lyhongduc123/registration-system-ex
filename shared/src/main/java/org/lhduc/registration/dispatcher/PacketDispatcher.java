package org.lhduc.registration.dispatcher;

import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.packet.PacketHandler;
import org.lhduc.registration.protocol.MessageType;

import java.util.HashMap;
import java.util.Map;

public class PacketDispatcher {

    private final Map<MessageType,
            PacketHandler<? extends Packet>>
            handlers;

    public PacketDispatcher() {
        handlers = new HashMap<MessageType, PacketHandler<? extends Packet>>();
    }

    @SuppressWarnings("unchecked")
    public void dispatch(Connection conn,
                         Packet packet) {

        PacketHandler handler =
                handlers.get(packet.getHeader().getType());

        if (handler == null) {
            throw new IllegalArgumentException("No handler for type: " + packet.getHeader().getType());
        }

        handler.handle(conn, packet);
    }

    public void registerHandler(MessageType type, PacketHandler<? extends Packet> handler) {
        handlers.put(type, handler);
    }
}
