package org.lhduc.registration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.config.ServerConfig;
import org.lhduc.registration.crypto.HmacUtil;
import org.lhduc.registration.models.Challenge;
import org.lhduc.registration.models.ClientCredential;
import org.lhduc.registration.models.ClientSession;
import org.lhduc.registration.models.SessionStatus;
import org.lhduc.registration.repository.ChallengeRepository;
import org.lhduc.registration.repository.RegistrationRepository;
import org.lhduc.registration.repository.SessionRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_SIZE = 32;

    private final RegistrationRepository registrationRepository;
    private final ChallengeRepository challengeRepository;
    private final SessionRepository sessionRepository;
    private final ServerConfig config;

    public Challenge initiateRegistration(UUID clientId) {
        ClientCredential credential = registrationRepository.get(clientId);
        if (credential == null) {
            credential = new ClientCredential(clientId, 0, "localhost", "dynamic", config.getSecret());
            registrationRepository.add(credential);
        }

        ClientSession existing = sessionRepository.get(clientId);
        if (existing != null && existing.getStatus() == SessionStatus.ACTIVE) {
            throw new IllegalStateException("Client already registered: " + clientId);
        }

        Challenge existingChallenge = challengeRepository.acquireForClient(clientId, config.getChallengeTimeout());
        if (existingChallenge != null) {
            return existingChallenge;
        }

        byte[] nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(nonce);

        Challenge challenge = Challenge.builder()
                .challengeId(UUID.randomUUID())
                .clientId(clientId)
                .nonce(nonce)
                .expiredAt(Instant.now().plus(config.getChallengeTimeout()))
                .build();

        challengeRepository.addChallenge(challenge);
        return challenge;
    }

    public ClientSession completeRegistration(UUID challengeId, UUID clientId, byte[] responseHash) {
        Challenge challenge = challengeRepository.validateAndMarkUsed(challengeId);
        if (challenge == null) {
            throw new IllegalStateException("Invalid or expired challenge: " + challengeId);
        }

        if (!challenge.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("ClientId mismatch for challenge: " + challengeId);
        }

        ClientCredential credential = registrationRepository.get(clientId);
        if (credential == null) {
            throw new IllegalArgumentException("Unknown client: " + clientId);
        }

        if (!HmacUtil.verify(credential.getClientSecret(), challenge.getNonce(), responseHash)) {
            throw new SecurityException("Authentication failed for client: " + clientId);
        }

        Instant now = Instant.now();
        ClientSession session = ClientSession.builder()
                .clientId(clientId)
                .sessionId(UUID.randomUUID())
                .status(SessionStatus.ACTIVE)
                .registeredAt(now)
                .expiredAt(now.plus(config.getLeaseDuration()))
                .build();

        sessionRepository.add(session);
        return session;
    }

    public ClientSession renew(UUID clientId, UUID sessionId) {
        Instant now = Instant.now();
        ClientSession[] result = new ClientSession[1];
        sessionRepository.computeIfPresent(clientId, session -> {
            if (session.getStatus() == SessionStatus.ACTIVE
                    && !session.getExpiredAt().isBefore(now)
                    && session.getSessionId().equals(sessionId)) {
                session.setExpiredAt(now.plus(config.getLeaseDuration()));
                session.setRegisteredAt(now);
                result[0] = session;
            }
            return session;
        });
        if (result[0] == null) {
            throw new IllegalStateException("No active session for client: " + clientId);
        }
        return result[0];
    }

    public void deregister(UUID clientId, UUID sessionId) {
        ClientSession session = sessionRepository.get(clientId);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("No active session for client: " + clientId);
        }
        if (!session.getSessionId().equals(sessionId)) {
            throw new SecurityException("SessionId mismatch for client: " + clientId);
        }
        session.setStatus(SessionStatus.CANCELLED);
        registrationRepository.delete(clientId);
        sessionRepository.delete(clientId);
    }

    public int cleanup() {
        int expired = 0;
        Instant now = Instant.now();
        for (ClientSession session : sessionRepository.getAll()) {
            boolean removed = sessionRepository.computeIfPresent(session.getClientId(), s -> {
                if (s.getStatus() == SessionStatus.ACTIVE && s.getExpiredAt().isBefore(now)) {
                    return null;
                }
                return s;
            }) == null;
            if (removed) {
                expired++;
                log.debug("Session {} for client {} expired", session.getSessionId(), session.getClientId());
                registrationRepository.delete(session.getClientId());
            }
        }
        if (expired > 0) {
            log.info("Expired {} sessions", expired);
        }
        return expired;
    }
}
