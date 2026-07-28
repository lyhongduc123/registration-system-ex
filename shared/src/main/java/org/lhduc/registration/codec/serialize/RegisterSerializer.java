package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.RegisterPacket;
import org.lhduc.registration.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RegisterSerializer implements PacketSerializer<RegisterPacket> {

    @Override
    public MessageType type() {
        return MessageType.REGISTER;
    }

    @Override
    public void write(OutputStream out, RegisterPacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeUTF(packet.getClientId());
        byte[] authHash = packet.getAuthHash();
        dos.writeInt(authHash.length);
        dos.write(authHash);
    }

    @Override
    public RegisterPacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        String clientId = dis.readUTF();
        int hashLength = dis.readInt();
        byte[] authHash = new byte[hashLength];
        dis.readFully(authHash);
        return new RegisterPacket(header, clientId, authHash);
    }
}
