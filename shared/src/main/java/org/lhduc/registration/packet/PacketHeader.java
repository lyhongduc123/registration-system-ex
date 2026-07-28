package org.lhduc.registration.packet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.lhduc.registration.protocol.MessageType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PacketHeader {
    private MessageType type;
    private int length;
    private int version;
    private UUID requestId;
    private Instant timestamp;
}
