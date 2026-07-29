package org.lhduc.registration.server;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.service.RegistrationService;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SessionExpirySweeper {

    private final RegistrationService registrationService;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;

    public SessionExpirySweeper(RegistrationService registrationService, Duration interval) {
        this.registrationService = registrationService;
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
        registrationService.cleanup();
    }
}