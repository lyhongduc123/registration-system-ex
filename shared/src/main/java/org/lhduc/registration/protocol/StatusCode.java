package org.lhduc.registration.protocol;

public enum StatusCode {
    SUCCESS,
    UNAUTHORIZED,
    LEASE_EXPIRED,
    INVALID_CHALLENGE,
    TIMEOUT,
    RETRY_LIMIT,
    SERVER_ERROR
}
