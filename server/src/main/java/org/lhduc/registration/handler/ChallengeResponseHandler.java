package org.lhduc.registration.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.models.ClientSession;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.ChallengeResponsePacket;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.PacketHandler;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.SuccessPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;
import org.lhduc.registration.service.RegistrationService;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ChallengeResponseHandler implements PacketHandler<ChallengeResponsePacket> {

    private final RegistrationService registrationService;

    @Override
    public MessageType type() {
        return MessageType.CHALLENGE_RESPONSE;
    }

    @Override
    public void handle(Connection conn, ChallengeResponsePacket packet) {
        UUID clientId = UUID.fromString(packet.getClientId());
        try {
            ClientSession session = registrationService.completeRegistration(
                    packet.getChallengeId(), clientId, packet.getResponseHash()
            );

            conn.send(new SuccessPacket(
                    PacketHeader.builder()
                            .type(MessageType.SUCCESS)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    StatusCode.SUCCESS,
                    session.getSessionId(),
                    session.getExpiredAt()
            ));
            log.info("Client {} registered, session {}", clientId, session.getSessionId());
        } catch (Exception e) {
            log.warn("Challenge response failed for client {}: {}", clientId, e.getMessage());
            sendError(conn, packet, e);
        }
    }

    private StatusCode toStatusCode(Exception e) {
        if (e instanceof SecurityException || e instanceof IllegalArgumentException) {
            return StatusCode.UNAUTHORIZED;
        }
        if (e instanceof IllegalStateException) {
            return StatusCode.INVALID_CHALLENGE;
        }
        return StatusCode.SERVER_ERROR;
    }

    private void sendError(Connection conn, ChallengeResponsePacket packet, Exception e) {
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
