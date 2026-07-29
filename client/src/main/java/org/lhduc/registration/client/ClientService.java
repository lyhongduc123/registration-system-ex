package org.lhduc.registration.client;

import lombok.extern.slf4j.Slf4j;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
public class ClientService {

    private final UUID clientId;
    private final String secret;
    private final String host;
    private final int port;
    private final int maxRetry;
    private final int renewBeforeSeconds;
    private final Duration leaseDuration;

    private UUID sessionId;
    private Instant leaseExpiry;
    private volatile boolean registered;

    public ClientService(UUID clientId, String secret, String host, int port,
                         int maxRetry, Duration leaseDuration, int renewBeforeSeconds) {
        this.clientId = clientId;
        this.secret = secret;
        this.host = host;
        this.port = port;
        this.maxRetry = maxRetry;
        this.leaseDuration = leaseDuration;
        this.renewBeforeSeconds = renewBeforeSeconds;
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

    public void register() throws IOException {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                doRegister();
                registered = true;
                log.debug("Client {} registered (session={})", clientId, sessionId);
                return;
            } catch (Exception e) {
                log.warn("Client {} register attempt {}/{} failed: {}",
                        clientId, attempt, maxRetry, e.getMessage());
                if (attempt < maxRetry) {
                    sleep(5000 * attempt);
                }
            }
        }
        throw new IOException("Client " + clientId + " failed to register after " + maxRetry + " attempts");
    }

    private void doRegister() throws IOException {
        try (Connection connection = new Connection(new Socket(host, port), createCodec())) {
            byte[] authHash = HmacUtil.compute(secret, clientId.toString().getBytes());
            RegisterPacket registerPacket = new RegisterPacket(
                    PacketHeader.builder()
                            .type(MessageType.REGISTER)
                            .requestId(UUID.randomUUID())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    clientId.toString(),
                    authHash
            );

            connection.send(registerPacket);
            Packet response = connection.readPacket();

            switch (response.getHeader().getType()) {
                case CHALLENGE -> handleChallenge(connection, (ChallengePacket) response);
                case ERROR -> {
                    ErrorPacket error = (ErrorPacket) response;
                    throw new IOException("Register rejected: " + error.getStatusCode() + " - " + error.getMessage());
                }
                default -> throw new IOException("Unexpected response type: " + response.getHeader().getType());
            }
        }
    }

    private void handleChallenge(Connection connection, ChallengePacket challengePacket) throws IOException {
        byte[] responseHash = HmacUtil.compute(secret, challengePacket.getNonce());

        ChallengeResponsePacket responsePacket = new ChallengeResponsePacket(
                PacketHeader.builder()
                        .type(MessageType.CHALLENGE_RESPONSE)
                        .requestId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .version(1)
                        .build(),
                clientId.toString(),
                challengePacket.getChallengeId(),
                responseHash
        );

        connection.send(responsePacket);
        Packet result = connection.readPacket();

        switch (result.getHeader().getType()) {
            case SUCCESS -> {
                SuccessPacket success = (SuccessPacket) result;
                this.sessionId = success.getSessionId();
                this.leaseExpiry = success.getLeaseExpiry();
            }
            case ERROR -> {
                ErrorPacket error = (ErrorPacket) result;
                throw new IOException("Challenge rejected: " + error.getStatusCode() + " - " + error.getMessage());
            }
            default -> throw new IOException("Unexpected response type: " + result.getHeader().getType());
        }
    }

    public void renew() throws IOException {
        if (!registered || sessionId == null) {
            throw new IllegalStateException("Client not registered: " + clientId);
        }

        try (Connection connection = new Connection(new Socket(host, port), createCodec())) {
            RenewPacket renewPacket = new RenewPacket(
                    PacketHeader.builder()
                            .type(MessageType.RENEW)
                            .requestId(UUID.randomUUID())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    clientId.toString(),
                    sessionId
            );

            connection.send(renewPacket);
            Packet response = connection.readPacket();

            switch (response.getHeader().getType()) {
                case RENEW_ACK -> {
                    RenewAckPacket ack = (RenewAckPacket) response;
                    if (ack.getStatusCode() == StatusCode.SUCCESS) {
                        this.leaseExpiry = ack.getNewLeaseExpiry();
                        log.debug("Client {} renewed, lease expiry {}", clientId, leaseExpiry);
                    } else {
                        throw new IOException("Renew rejected: " + ack.getStatusCode());
                    }
                }
                case ERROR -> {
                    ErrorPacket error = (ErrorPacket) response;
                    throw new IOException("Renew error: " + error.getStatusCode() + " - " + error.getMessage());
                }
                default -> throw new IOException("Unexpected response to renew: " + response.getHeader().getType());
            }
        }
    }

    public void deregister() throws IOException {
        if (!registered || sessionId == null) {
            return;
        }

        try (Connection connection = new Connection(new Socket(host, port), createCodec())) {
            DeregisterPacket deregisterPacket = new DeregisterPacket(
                    PacketHeader.builder()
                            .type(MessageType.DEREGISTER)
                            .requestId(UUID.randomUUID())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    clientId.toString(),
                    sessionId
            );

            connection.send(deregisterPacket);
            Packet response = connection.readPacket();

            switch (response.getHeader().getType()) {
                case ACK -> log.debug("Client {} deregistered", clientId);
                case ERROR -> {
                    ErrorPacket error = (ErrorPacket) response;
                    log.warn("Deregister failed for client {}: {} - {}",
                            clientId, error.getStatusCode(), error.getMessage());
                }
                default -> log.warn("Unexpected deregister response: {}", response.getHeader().getType());
            }
        }
        registered = false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getLeaseExpiry() {
        return leaseExpiry;
    }
}
