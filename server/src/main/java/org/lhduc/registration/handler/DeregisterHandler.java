package org.lhduc.registration.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.DeregisterPacket;
import org.lhduc.registration.packet.AckPacket;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.PacketHandler;
import org.lhduc.registration.packet.PacketHeader;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;
import org.lhduc.registration.service.RegistrationService;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class DeregisterHandler implements PacketHandler<DeregisterPacket> {

    private final RegistrationService registrationService;

    @Override
    public MessageType type() {
        return MessageType.DEREGISTER;
    }

    @Override
    public void handle(Connection conn, DeregisterPacket packet) {
        UUID clientId = UUID.fromString(packet.getClientId());
        try {
            registrationService.deregister(clientId, packet.getSessionId());
            conn.send(new AckPacket(
                    PacketHeader.builder()
                            .type(MessageType.ACK)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build()
            ));
            log.info("Client {} deregistered", clientId);
        } catch (Exception e) {
            log.warn("Deregister failed for client {}: {}", clientId, e.getMessage());
            sendError(conn, packet, e);
        }
    }

    private void sendError(Connection conn, DeregisterPacket packet, Exception e) {
        try {
            conn.send(new ErrorPacket(
                    PacketHeader.builder()
                            .type(MessageType.ERROR)
                            .requestId(packet.getHeader().getRequestId())
                            .timestamp(Instant.now())
                            .version(1)
                            .build(),
                    StatusCode.SERVER_ERROR,
                    e.getMessage()
            ));
        } catch (Exception ex) {
            log.error("Failed to send error to client", ex);
        }
    }
}
