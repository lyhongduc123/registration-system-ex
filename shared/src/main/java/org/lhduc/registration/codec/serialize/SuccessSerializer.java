package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.SuccessPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.UUID;

public class SuccessSerializer implements PacketSerializer<SuccessPacket> {

    @Override
    public MessageType type() {
        return MessageType.SUCCESS;
    }

    @Override
    public void write(OutputStream out, SuccessPacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeByte(packet.getStatusCode().getValue());
        dos.writeLong(packet.getSessionId().getMostSignificantBits());
        dos.writeLong(packet.getSessionId().getLeastSignificantBits());
        dos.writeLong(packet.getLeaseExpiry().toEpochMilli());
    }

    @Override
    public SuccessPacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        StatusCode statusCode = StatusCode.fromValue(dis.readUnsignedByte());
        UUID sessionId = new UUID(dis.readLong(), dis.readLong());
        Instant leaseExpiry = Instant.ofEpochMilli(dis.readLong());
        return new SuccessPacket(header, statusCode, sessionId, leaseExpiry);
    }
}
