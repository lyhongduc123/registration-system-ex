package org.lhduc.registration.codec;

import org.lhduc.registration.codec.serialize.PacketSerializer;
import org.lhduc.registration.packet.Header;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.protocol.MessageType;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    }

    public Packet read(InputStream input) throws IOException {
        DataInputStream dataInput =
                new DataInputStream(input);

        MessageType type =
                MessageType.valueOf(dataInput.readUTF());
        // Not use for now
        UUID txId =
                new UUID(dataInput.readLong(),
                        dataInput.readLong());

        PacketSerializer serializer =
                serializers.get(type);

        return serializer.read(dataInput);
    }

    public void registerSerializer(MessageType type, PacketSerializer serializer) {
        serializers.put(type, serializer);
    }
}
