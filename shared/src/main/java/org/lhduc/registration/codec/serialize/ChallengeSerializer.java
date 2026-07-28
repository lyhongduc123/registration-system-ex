package org.lhduc.registration.codec.serialize;

import org.lhduc.registration.packet.ChallengePacket;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.UUID;

public class ChallengeSerializer implements PacketSerializer<ChallengePacket> {

    @Override
    public MessageType type() {
        return MessageType.CHALLENGE;
    }

    @Override
    public void write(OutputStream out, ChallengePacket packet) throws IOException {
        DataOutputStream dos = (DataOutputStream) out;
        dos.writeLong(packet.getChallengeId().getMostSignificantBits());
        dos.writeLong(packet.getChallengeId().getLeastSignificantBits());
        byte[] nonce = packet.getNonce();
        dos.writeInt(nonce.length);
        dos.write(nonce);
        dos.writeLong(packet.getExpiry().toSeconds());
    }

    @Override
    public ChallengePacket read(InputStream in, PacketHeader header) throws IOException {
        DataInputStream dis = (DataInputStream) in;
        UUID challengeId = new UUID(dis.readLong(), dis.readLong());
        int nonceLength = dis.readInt();
        byte[] nonce = new byte[nonceLength];
        dis.readFully(nonce);
        Duration expiry = Duration.ofSeconds(dis.readLong());
        return new ChallengePacket(header, challengeId, nonce, expiry);
    }
}
