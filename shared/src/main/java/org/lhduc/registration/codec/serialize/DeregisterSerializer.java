package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.DeregisterPacket;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class DeregisterSerializer implements PacketSerializer<DeregisterPacket> {

    @Override
    public MessageType type() {
        return MessageType.DEREGISTER;
    }

    @Override
    public void write(OutputStream out, DeregisterPacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeUTF(packet.getClientId());
        dos.writeLong(packet.getSessionId().getMostSignificantBits());
        dos.writeLong(packet.getSessionId().getLeastSignificantBits());
    }

    @Override
    public DeregisterPacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        String clientId = dis.readUTF();
        UUID sessionId = new UUID(dis.readLong(), dis.readLong());
        return new DeregisterPacket(header, clientId, sessionId);
    }
}
