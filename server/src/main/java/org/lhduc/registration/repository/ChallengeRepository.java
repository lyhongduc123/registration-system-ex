package org.lhduc.registration.repository;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.models.Challenge;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ChallengeRepository {
    private final ConcurrentMap<UUID, Challenge> challenges;
    private final ConcurrentMap<UUID, Challenge> invalidatedChallenges;

    public ChallengeRepository() {
        challenges = new ConcurrentHashMap<>();
        invalidatedChallenges = new ConcurrentHashMap<>();
    }

    public Challenge addChallenge(Challenge challenge) {
        challenges.put(challenge.getChallengeId(), challenge);
        return challenge;
    }

    public Challenge getByChallengeId(UUID challengeId) {
        return challenges.get(challengeId);
    }

    public void invalidateChallenge(UUID challengeId) {
        if (invalidatedChallenges.containsKey(challengeId)) {
            log.warn("Challenge {} already invalidated", challengeId);
            return;
        }
        Challenge challenge = challenges.get(challengeId);
        if (challenge == null) {
            log.warn("Challenge {} not found", challengeId);
            return;
        }
        challenge.setUsed(true);
        invalidatedChallenges.put(challengeId, challenge);
        challenges.remove(challengeId);
    }

    public boolean isChallengeValid(UUID challengeId) {
        Challenge challenge = challenges.get(challengeId);
        return challenge != null
                && !challenge.isUsed()
                && challenge.getExpiredAt().isAfter(Instant.now());
    }
}
