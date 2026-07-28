package org.lhduc.registration.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.models.ClientSession;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.PacketHandler;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.packet.RenewAckPacket;
import org.lhduc.registration.packet.RenewPacket;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;
import org.lhduc.registration.service.RegistrationService;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RenewHandler implements PacketHandler<RenewPacket> {

    private final RegistrationService registrationService;

    @Override
    public MessageType type() {
        return MessageType.RENEW;
    }

    @Override
    public void handle(Connection conn, RenewPacket packet) {
        UUID clientId = UUID.fromString(packet.getClientId());
        try {
            ClientSession session = registrationService.renew(clientId, packet.getSessionId());

            conn.send(new RenewAckPacket(
                    PacketHeader.builder()
                            .type(MessageType.RENEW_ACK)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    StatusCode.SUCCESS,
                    session.getExpiredAt()
            ));
            log.info("Client {} renewed, expiry {}", clientId, session.getExpiredAt());
        } catch (Exception e) {
            log.warn("Renew failed for client {}: {}", clientId, e.getMessage());
            sendError(conn, packet, e);
        }
    }

    private StatusCode toStatusCode(Exception e) {
        if (e instanceof SecurityException) {
            return StatusCode.UNAUTHORIZED;
        }
        if (e instanceof IllegalStateException) {
            return StatusCode.LEASE_EXPIRED;
        }
        return StatusCode.SERVER_ERROR;
    }

    private void sendError(Connection conn, RenewPacket packet, Exception e) {
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
