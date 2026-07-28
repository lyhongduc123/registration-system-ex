package org.lhduc.registration.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.models.Challenge;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.ChallengePacket;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.PacketHandler;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.RegisterPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;
import org.lhduc.registration.service.RegistrationService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RegisterHandler implements PacketHandler<RegisterPacket> {

    private final RegistrationService registrationService;

    @Override
    public MessageType type() {
        return MessageType.REGISTER;
    }

    @Override
    public void handle(Connection conn, RegisterPacket packet) {
        UUID clientId = UUID.fromString(packet.getClientId());
        try {
            Challenge challenge = registrationService.initiateRegistration(clientId);

            Duration remaining = Duration.between(Instant.now(), challenge.getExpiredAt());
            conn.send(new ChallengePacket(
                    PacketHeader.builder()
                            .type(MessageType.CHALLENGE)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    challenge.getChallengeId(),
                    challenge.getNonce(),
                    remaining.isNegative() ? Duration.ZERO : remaining
            ));
            log.info("Sent challenge {} to client {}", challenge.getChallengeId(), clientId);
        } catch (Exception e) {
            log.warn("Register failed for client {}: {}", clientId, e.getMessage());
            sendError(conn, packet, e);
        }
    }

    private StatusCode toStatusCode(Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return StatusCode.UNAUTHORIZED;
        }
        return StatusCode.SERVER_ERROR;
    }

    private void sendError(Connection conn, RegisterPacket packet, Exception e) {
        try {
            conn.send(new ErrorPacket(
                    PacketHeader.builder()
                            .type(MessageType.ERROR)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    toStatusCode(e),
                    e.getMessage()
            ));
        } catch (Exception ex) {
            log.error("Failed to send error to client", ex);
        }
    }
}
