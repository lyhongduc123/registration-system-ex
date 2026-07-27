package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.RegisterPacket;
import org.lhduc.registration.protocol.MessageType;

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

    }

    @Override
    public RegisterPacket read(InputStream in) throws IOException {
        return null;
    }
}
