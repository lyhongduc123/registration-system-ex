package org.lhduc.registration.client;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.codec.PacketCodec;
import org.lhduc.registration.codec.serialize.ChallengeResponseSerializer;
import org.lhduc.registration.codec.serialize.ChallengeSerializer;
import org.lhduc.registration.codec.serialize.ErrorSerializer;
import org.lhduc.registration.codec.serialize.RegisterSerializer;
import org.lhduc.registration.codec.serialize.RenewAckSerializer;
import org.lhduc.registration.codec.serialize.RenewSerializer;
import org.lhduc.registration.codec.serialize.SuccessSerializer;
import org.lhduc.registration.crypto.HmacUtil;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.ChallengePacket;
import org.lhduc.registration.packet.ChallengeResponsePacket;
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
public class ClientService implements Runnable {

    private final UUID clientId;
    private final String secret;
    private final Connection connection;
    private final int maxRetry;
    private final int renewBeforeSeconds;
    private final Duration leaseDuration;

    private UUID sessionId;
    private Instant leaseExpiry;
    private volatile boolean registered;
    private volatile boolean running;

    public ClientService(UUID clientId, String secret, String host, int port,
                         int maxRetry, Duration leaseDuration, int renewBeforeSeconds) throws IOException {
        this.clientId = clientId;
        this.secret = secret;
        this.maxRetry = maxRetry;
        this.leaseDuration = leaseDuration;
        this.renewBeforeSeconds = renewBeforeSeconds;
        this.connection = new Connection(new Socket(host, port), createCodec());
        this.running = true;
    }

    private static PacketCodec createCodec() {
        PacketCodec codec = new PacketCodec();
        codec.registerSerializer(MessageType.REGISTER, new RegisterSerializer());
        codec.registerSerializer(MessageType.CHALLENGE, new ChallengeSerializer());
        codec.registerSerializer(MessageType.CHALLENGE_RESPONSE, new ChallengeResponseSerializer());
        codec.registerSerializer(MessageType.RENEW, new RenewSerializer());
        codec.registerSerializer(MessageType.RENEW_ACK, new RenewAckSerializer());
        codec.registerSerializer(MessageType.SUCCESS, new SuccessSerializer());
        codec.registerSerializer(MessageType.ERROR, new ErrorSerializer());
        return codec;
    }

    @Override
    public void run() {
        try {
            registerWithRetry();
            if (registered) {
                renewLoop();
            }
        } catch (Exception e) {
            log.error("Client {} failed: {}", clientId, e.getMessage());
        } finally {
            close();
        }
    }

    private void registerWithRetry() {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                doRegister();
                registered = true;
                log.info("Client {} registered successfully (session={})", clientId, sessionId);
                return;
            } catch (Exception e) {
                log.warn("Client {} register attempt {}/{} failed: {}",
                        clientId, attempt, maxRetry, e.getMessage());
                if (attempt < maxRetry) {
                    sleep(1000);
                }
            }
        }
        log.error("Client {} failed to register after {} attempts", clientId, maxRetry);
    }

    private void doRegister() throws IOException {
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
            case CHALLENGE -> handleChallenge((ChallengePacket) response);
            case ERROR -> {
                ErrorPacket error = (ErrorPacket) response;
                throw new IOException("Register rejected: " + error.getStatusCode() + " - " + error.getMessage());
            }
            default -> throw new IOException("Unexpected response type: " + response.getHeader().getType());
        }
    }

    private void handleChallenge(ChallengePacket challengePacket) throws IOException {
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

    private void renewLoop() {
        while (running && registered) {
            try {
                long secsUntilExpiry = Duration.between(Instant.now(), leaseExpiry).getSeconds();
                if (secsUntilExpiry <= renewBeforeSeconds) {
                    doRenew();
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Client {} renew failed: {}", clientId, e.getMessage());
                if (leaseExpiry != null && Instant.now().isAfter(leaseExpiry)) {
                    log.warn("Client {} lease expired, re-registering", clientId);
                    registered = false;
                    registerWithRetry();
                }
            }
        }
    }

    private void doRenew() throws IOException {
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
                    log.info("Client {} renewed, lease expiry {}", clientId, leaseExpiry);
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

    private void close() {
        running = false;
        try {
            connection.close();
        } catch (IOException e) {
            log.warn("Error closing client {} connection: {}", clientId, e.getMessage());
        }
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

    public void stop() {
        this.running = false;
    }

    public UUID getClientId() {
        return clientId;
    }
}