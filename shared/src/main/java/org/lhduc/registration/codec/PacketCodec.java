package org.lhduc.registration.codec;

import org.lhduc.registration.codec.serialize.PacketSerializer;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.protocol.MessageType;

import java.io.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PacketCodec {

    private final Map<MessageType,
                PacketSerializer<? extends Packet>>
            serializers;

    public PacketCodec() {
        serializers = new HashMap<MessageType, PacketSerializer<? extends Packet>>();
    }

    public void write(OutputStream out, Packet packet) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        PacketHeader header = packet.getHeader();
        dos.writeByte(header.getType().getValue());
        dos.writeInt(header.getLength());
        dos.writeLong(header.getRequestId().getMostSignificantBits());
        dos.writeLong(header.getRequestId().getLeastSignificantBits());
        dos.writeInt(header.getVersion());
        dos.writeLong(header.getTimestamp().toEpochMilli());

        PacketSerializer serializer = serializers.get(header.getType());
        serializer.write(dos, packet);
    }

    public Packet read(InputStream input) throws IOException {
        DataInputStream dataInput =
                new DataInputStream(input);

        PacketHeader header = readHeader(dataInput);

        PacketSerializer serializer =
                serializers.get(header.getType());

        return serializer.read(dataInput, header);
    }

    private PacketHeader readHeader(DataInputStream dataInputStream) throws IOException {
        MessageType type =
                MessageType.fromValue(dataInputStream.readByte());
        int length = dataInputStream.readInt();
        UUID requestId = new UUID(
                dataInputStream.readLong(),
                dataInputStream.readLong()
        );
        int version = dataInputStream.readInt();
        Instant timestamp = Instant.ofEpochMilli(dataInputStream.readLong());
        return new PacketHeader(
                type,
                0,
                version,
                requestId,
                timestamp
        );
    }

    public void registerSerializer(MessageType type, PacketSerializer serializer) {
        serializers.put(type, serializer);
    }
}
