package org.lhduc.registration.network;

import lombok.Getter;
import org.lhduc.registration.codec.PacketCodec;
import org.lhduc.registration.packet.Packet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Getter
public class Connection implements AutoCloseable {
    private final Socket socket;
    private final PacketCodec codec;

    private final InputStream in;
    private final OutputStream out;

    public Connection(Socket socket, PacketCodec codec) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.codec = codec;
    }

    public Packet readPacket() throws IOException {
        return codec.read(in);
    }

    public void send(Packet packet) throws IOException {
        codec.write(out, packet);
        out.flush();
    }

    public void close() throws IOException {
        socket.close();
    }
}
