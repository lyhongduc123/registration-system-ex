package org.lhduc.registration.service;

import lombok.RequiredArgsConstructor;
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
            throw new IllegalArgumentException("Unknown client: " + clientId);
        }

        ClientSession existing = sessionRepository.get(clientId);
        if (existing != null && existing.getStatus() == SessionStatus.ACTIVE) {
            throw new IllegalStateException("Client already registered: " + clientId);
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
        if (!challengeRepository.isChallengeValid(challengeId)) {
            throw new IllegalStateException("Invalid or expired challenge: " + challengeId);
        }

        Challenge challenge = challengeRepository.getByChallengeId(challengeId);

        ClientCredential credential = registrationRepository.get(clientId);
        if (credential == null) {
            challengeRepository.invalidateChallenge(challengeId);
            throw new IllegalArgumentException("Unknown client: " + clientId);
        }

        if (!HmacUtil.verify(credential.getClientSecret(), challenge.getNonce(), responseHash)) {
            challengeRepository.invalidateChallenge(challengeId);
            throw new SecurityException("Authentication failed for client: " + clientId);
        }

        challengeRepository.invalidateChallenge(challengeId);

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
        ClientSession session = sessionRepository.get(clientId);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("No active session for client: " + clientId);
        }
        if (!session.getSessionId().equals(sessionId)) {
            throw new SecurityException("SessionId mismatch for client: " + clientId);
        }

        Instant now = Instant.now();
        session.setExpiredAt(now.plus(config.getLeaseDuration()));
        session.setRegisteredAt(now);
        return session;
    }

    public void deregister(UUID clientId) {
        ClientSession session = sessionRepository.get(clientId);
        if (session != null) {
            session.setStatus(SessionStatus.CANCELLED);
        }
        registrationRepository.delete(clientId);
        sessionRepository.delete(clientId);
    }
}
