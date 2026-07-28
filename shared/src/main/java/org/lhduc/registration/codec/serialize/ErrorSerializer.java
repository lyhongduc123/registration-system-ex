package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ErrorSerializer implements PacketSerializer<ErrorPacket> {

    @Override
    public MessageType type() {
        return MessageType.ERROR;
    }

    @Override
    public void write(OutputStream out, ErrorPacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeByte(packet.getStatusCode().getValue());
        dos.writeUTF(packet.getMessage());
    }

    @Override
    public ErrorPacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        StatusCode statusCode = StatusCode.fromValue(dis.readUnsignedByte());
        String message = dis.readUTF();
        return new ErrorPacket(header, statusCode, message);
    }
}
