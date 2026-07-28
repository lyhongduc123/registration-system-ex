package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.RenewAckPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;

public class RenewAckSerializer implements PacketSerializer<RenewAckPacket> {

    @Override
    public MessageType type() {
        return MessageType.RENEW_ACK;
    }

    @Override
    public void write(OutputStream out, RenewAckPacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeByte(packet.getStatusCode().getValue());
        dos.writeLong(packet.getNewLeaseExpiry().toEpochMilli());
    }

    @Override
    public RenewAckPacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        StatusCode statusCode = StatusCode.fromValue(dis.readUnsignedByte());
        Instant newLeaseExpiry = Instant.ofEpochMilli(dis.readLong());
        return new RenewAckPacket(header, statusCode, newLeaseExpiry);
    }
}
