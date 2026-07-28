package org.lhduc.registration.server;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.models.ClientSession;
import org.lhduc.registration.models.SessionStatus;
import org.lhduc.registration.repository.SessionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SessionExpirySweeper {

    private final SessionRepository sessionRepository;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;

    public SessionExpirySweeper(SessionRepository sessionRepository, Duration interval) {
        this.sessionRepository = sessionRepository;
        this.interval = interval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-expiry-sweeper");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sweep, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Session expiry sweeper started (interval={})", interval);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void sweep() {
        int expired = 0;
        for (ClientSession session : sessionRepository.getAll()) {
            if (session.getStatus() == SessionStatus.ACTIVE && session.getExpiredAt().isBefore(Instant.now())) {
                session.setStatus(SessionStatus.EXPIRED);
                expired++;
                log.debug("Session {} for client {} expired", session.getSessionId(), session.getClientId());
            }
        }
        if (expired > 0) {
            log.info("Expired {} sessions", expired);
        }
    }
}