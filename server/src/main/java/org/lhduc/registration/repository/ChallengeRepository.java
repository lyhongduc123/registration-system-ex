package org.lhduc.registration.repository;

import org.lhduc.registration.models.Challenge;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChallengeRepository {
    private final ConcurrentMap<UUID, Challenge> challenges;
    private final ConcurrentMap<UUID, UUID> clientChallengeIds;

    public ChallengeRepository() {
        challenges = new ConcurrentHashMap<>();
        clientChallengeIds = new ConcurrentHashMap<>();
    }

    public Challenge addChallenge(Challenge challenge) {
        challenges.put(challenge.getChallengeId(), challenge);
        clientChallengeIds.put(challenge.getClientId(), challenge.getChallengeId());
        return challenge;
    }

    public Challenge getByChallengeId(UUID challengeId) {
        return challenges.get(challengeId);
    }

    public Challenge validateAndMarkUsed(UUID challengeId) {
        return challenges.compute(challengeId, (id, c) -> {
            if (c == null || c.isUsed() || c.getExpiredAt().isBefore(Instant.now())) {
                return null;
            }
            c.setUsed(true);
            return c;
        });
    }

    public Challenge acquireForClient(UUID clientId, Duration challengeTimeout) {
        UUID challengeId = clientChallengeIds.get(clientId);
        if (challengeId == null) return null;

        Challenge result = challenges.compute(challengeId, (id, c) -> {
            if (c == null || c.isUsed() || c.getExpiredAt().isBefore(Instant.now())) {
                return null;
            }
            return c;
        });

        if (result == null) {
            clientChallengeIds.remove(clientId, challengeId);
            return null;
        }

        if (result.getExpiredAt().minus(challengeTimeout.dividedBy(3)).isBefore(Instant.now())) {
            return null;
        }

        return result;
    }

    public int cleanup() {
        Instant now = Instant.now();
        int[] count = new int[1];
        challenges.entrySet().removeIf(entry -> {
            Challenge c = entry.getValue();
            if (c.isUsed() || c.getExpiredAt().isBefore(now)) {
                clientChallengeIds.remove(c.getClientId(), entry.getKey());
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }
}
