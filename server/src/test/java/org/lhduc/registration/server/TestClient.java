package org.lhduc.registration.server;

import org.lhduc.registration.codec.PacketCodec;
import org.lhduc.registration.codec.serialize.AckSerializer;
import org.lhduc.registration.codec.serialize.ChallengeResponseSerializer;
import org.lhduc.registration.codec.serialize.ChallengeSerializer;
import org.lhduc.registration.codec.serialize.DeregisterSerializer;
import org.lhduc.registration.codec.serialize.ErrorSerializer;
import org.lhduc.registration.codec.serialize.RegisterSerializer;
import org.lhduc.registration.codec.serialize.RenewAckSerializer;
import org.lhduc.registration.codec.serialize.RenewSerializer;
import org.lhduc.registration.codec.serialize.SuccessSerializer;
import org.lhduc.registration.crypto.HmacUtil;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.ChallengePacket;
import org.lhduc.registration.packet.ChallengeResponsePacket;
import org.lhduc.registration.packet.DeregisterPacket;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.RegisterPacket;
import org.lhduc.registration.packet.RenewAckPacket;
import org.lhduc.registration.packet.RenewPacket;
import org.lhduc.registration.packet.SuccessPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;

import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;

public class TestClient implements AutoCloseable {

    private final UUID clientId;
    private final String secret;
    private final Connection connection;

    private UUID sessionId;
    private boolean registered;

    public TestClient(String host, int port, String secret) throws IOException {
        this.clientId = UUID.randomUUID();
        this.secret = secret;
        this.connection = new Connection(new Socket(host, port), createCodec());
    }

    public TestClient(UUID clientId, String host, int port, String secret) throws IOException {
        this.clientId = clientId;
        this.secret = secret;
        this.connection = new Connection(new Socket(host, port), createCodec());
    }

    private static PacketCodec createCodec() {
        PacketCodec codec = new PacketCodec();
        codec.registerSerializer(MessageType.REGISTER, new RegisterSerializer());
        codec.registerSerializer(MessageType.CHALLENGE, new ChallengeSerializer());
        codec.registerSerializer(MessageType.CHALLENGE_RESPONSE, new ChallengeResponseSerializer());
        codec.registerSerializer(MessageType.RENEW, new RenewSerializer());
        codec.registerSerializer(MessageType.RENEW_ACK, new RenewAckSerializer());
        codec.registerSerializer(MessageType.SUCCESS, new SuccessSerializer());
        codec.registerSerializer(MessageType.DEREGISTER, new DeregisterSerializer());
        codec.registerSerializer(MessageType.ACK, new AckSerializer());
        codec.registerSerializer(MessageType.ERROR, new ErrorSerializer());
        return codec;
    }

    private PacketHeader header(MessageType type) {
        return PacketHeader.builder()
                .type(type)
                .requestId(UUID.randomUUID())
                .timestamp(Instant.now())
                .version(1)
                .build();
    }

    public UUID getClientId() {
        return clientId;
    }

    public boolean isRegistered() {
        return registered;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void register() throws IOException {
        byte[] authHash = HmacUtil.compute(secret, clientId.toString().getBytes());
        connection.send(new RegisterPacket(header(MessageType.REGISTER), clientId.toString(), authHash));

        Packet response = connection.readPacket();
        if (response.getHeader().getType() != MessageType.CHALLENGE) {
            throw new IOException("Expected CHALLENGE, got " + response.getHeader().getType());
        }

        ChallengePacket challenge = (ChallengePacket) response;
        byte[] responseHash = HmacUtil.compute(secret, challenge.getNonce());
        connection.send(new ChallengeResponsePacket(
                header(MessageType.CHALLENGE_RESPONSE), clientId.toString(),
                challenge.getChallengeId(), responseHash));

        Packet result = connection.readPacket();
        if (result.getHeader().getType() == MessageType.SUCCESS) {
            SuccessPacket success = (SuccessPacket) result;
            this.sessionId = success.getSessionId();
            this.registered = true;
        } else if (result.getHeader().getType() == MessageType.ERROR) {
            ErrorPacket error = (ErrorPacket) result;
            throw new IOException("Register failed: " + error.getStatusCode() + " - " + error.getMessage());
        } else {
            throw new IOException("Unexpected response: " + result.getHeader().getType());
        }
    }

    public void registerExpectingError() throws IOException {
        byte[] authHash = HmacUtil.compute(secret, clientId.toString().getBytes());
        connection.send(new RegisterPacket(header(MessageType.REGISTER), clientId.toString(), authHash));

        Packet response = connection.readPacket();
        if (response.getHeader().getType() != MessageType.CHALLENGE) {
            throw new IOException("Expected CHALLENGE, got " + response.getHeader().getType());
        }

        ChallengePacket challenge = (ChallengePacket) response;
        byte[] wrongHash = HmacUtil.compute("wrongSecret", challenge.getNonce());
        connection.send(new ChallengeResponsePacket(
                header(MessageType.CHALLENGE_RESPONSE), clientId.toString(),
                challenge.getChallengeId(), wrongHash));

        Packet result = connection.readPacket();
        if (result.getHeader().getType() == MessageType.ERROR) {
            throw new AuthFailedException(((ErrorPacket) result).getStatusCode());
        }
        throw new IOException("Expected ERROR, got " + result.getHeader().getType());
    }

    public void deregister() throws IOException {
        UUID sid = sessionId != null ? sessionId : UUID.randomUUID();
        connection.send(new DeregisterPacket(header(MessageType.DEREGISTER), clientId.toString(), sid));
        Packet result = connection.readPacket();
        if (result.getHeader().getType() == MessageType.ACK) {
            this.registered = false;
        } else if (result.getHeader().getType() == MessageType.ERROR) {
            ErrorPacket error = (ErrorPacket) result;
            throw new IOException("Deregister failed: " + error.getStatusCode() + " - " + error.getMessage());
        } else {
            throw new IOException("Unexpected deregister response: " + result.getHeader().getType());
        }
    }

    public void renew() throws IOException {
        connection.send(new RenewPacket(header(MessageType.RENEW), clientId.toString(), sessionId));
        Packet result = connection.readPacket();
        if (result.getHeader().getType() == MessageType.RENEW_ACK) {
            RenewAckPacket ack = (RenewAckPacket) result;
            if (ack.getStatusCode() == StatusCode.SUCCESS) {
                return;
            }
            throw new IOException("Renew failed: " + ack.getStatusCode());
        } else if (result.getHeader().getType() == MessageType.ERROR) {
            ErrorPacket error = (ErrorPacket) result;
            throw new IOException("Renew error: " + error.getStatusCode() + " - " + error.getMessage());
        } else {
            throw new IOException("Unexpected renew response: " + result.getHeader().getType());
        }
    }

    public ChallengePacket sendRegisterOnly() throws IOException {
        byte[] authHash = HmacUtil.compute(secret, clientId.toString().getBytes());
        connection.send(new RegisterPacket(header(MessageType.REGISTER), clientId.toString(), authHash));
        Packet response = connection.readPacket();
        if (response.getHeader().getType() != MessageType.CHALLENGE) {
            throw new IOException("Expected CHALLENGE, got " + response.getHeader().getType());
        }
        return (ChallengePacket) response;
    }

    public Packet sendChallengeResponse(UUID challengeId, byte[] nonce, String overrideSecret) throws IOException {
        String actualSecret = overrideSecret != null ? overrideSecret : secret;
        byte[] responseHash = HmacUtil.compute(actualSecret, nonce);
        connection.send(new ChallengeResponsePacket(
                header(MessageType.CHALLENGE_RESPONSE), clientId.toString(), challengeId, responseHash));
        return connection.readPacket();
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }

    public static class AuthFailedException extends IOException {
        private final StatusCode statusCode;

        public AuthFailedException(StatusCode statusCode) {
            super("Auth failed: " + statusCode);
            this.statusCode = statusCode;
        }

        public StatusCode getStatusCode() {
            return statusCode;
        }
    }
}
