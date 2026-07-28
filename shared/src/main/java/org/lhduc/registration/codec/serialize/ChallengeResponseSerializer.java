package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.ChallengeResponsePacket;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ChallengeResponseSerializer implements PacketSerializer<ChallengeResponsePacket> {

    @Override
    public MessageType type() {
        return MessageType.CHALLENGE_RESPONSE;
    }

    @Override
    public void write(OutputStream out, ChallengeResponsePacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeUTF(packet.getClientId());
        dos.writeLong(packet.getChallengeId().getMostSignificantBits());
        dos.writeLong(packet.getChallengeId().getLeastSignificantBits());
        byte[] responseHash = packet.getResponseHash();
        dos.writeInt(responseHash.length);
        dos.write(responseHash);
    }

    @Override
    public ChallengeResponsePacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        String clientId = dis.readUTF();
        UUID challengeId = new UUID(dis.readLong(), dis.readLong());
        int hashLength = dis.readInt();
        byte[] responseHash = new byte[hashLength];
        dis.readFully(responseHash);
        return new ChallengeResponsePacket(header, clientId, challengeId, responseHash);
    }
}
