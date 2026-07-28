package org.lhduc.registration.protocol;

import lombok.Getter;

@Getter
public enum StatusCode {
    SUCCESS(0),
    UNAUTHORIZED(1),
    LEASE_EXPIRED(2),
    INVALID_CHALLENGE(3),
    TIMEOUT(4),
    RETRY_LIMIT(5),
    SERVER_ERROR(6);

    private final int value;

    StatusCode(int value) {
        this.value = value;
    }

    public static StatusCode fromValue(int value) {
        for (StatusCode code : values()) {
            if (code.value == value) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + value);
    }
}
