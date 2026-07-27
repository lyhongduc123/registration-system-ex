package org.lhduc.registration.packet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.lhduc.registration.protocol.MessageType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class Header {
    private MessageType type;
    private int length;
    private int version;
    private UUID requestId;
    private Instant timestamp;
}
