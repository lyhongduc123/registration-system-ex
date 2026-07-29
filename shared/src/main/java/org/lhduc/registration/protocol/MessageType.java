package org.lhduc.registration.protocol;

import lombok.Getter;

@Getter
public enum MessageType {
    REGISTER(0),
    CHALLENGE(1),
    CHALLENGE_RESPONSE(2),
    RENEW(3),
    RENEW_ACK(4),
    SUCCESS(5),
    DEREGISTER(6),
    ACK(7),
    ERROR(-1);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public static MessageType fromValue(int value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
}
