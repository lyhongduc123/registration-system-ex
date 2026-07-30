package org.lhduc.registration.client;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

@Slf4j
public class ClientSimulator {

    public enum Mode {
        LOAD,
        STRESS
    }

    private final ClientConfig config;
    private final List<ClientService> clients;
    private final AtomicInteger successCount;
    private final AtomicInteger failCount;
    private final AtomicInteger timeoutCount;
    private final AtomicInteger totalAttempts;
    private final AtomicLong totalDurationNanos;
    private final LongAccumulator minDurationNanos;
    private final LongAccumulator maxDurationNanos;
    private Mode currentMode;
    private volatile long startTimeMillis;

    public ClientSimulator(ClientConfig config) {
        this.config = config;
        this.clients = new ArrayList<>();
        this.successCount = new AtomicInteger(0);
        this.failCount = new AtomicInteger(0);
        this.timeoutCount = new AtomicInteger(0);
        this.totalAttempts = new AtomicInteger(0);
        this.totalDurationNanos = new AtomicLong(0);
        this.minDurationNanos = new LongAccumulator(Long::min, Long.MAX_VALUE);
        this.maxDurationNanos = new LongAccumulator(Long::max, Long.MIN_VALUE);
    }

    public void start() throws InterruptedException {
        start(Mode.STRESS);
    }

    public void start(Mode mode) throws InterruptedException {
        start(mode, 0);
    }

    public void start(Mode mode, int idOffset) throws InterruptedException {
        clients.clear();
        currentMode = mode;
        successCount.set(0);
        failCount.set(0);
        timeoutCount.set(0);
        totalAttempts.set(0);
        totalDurationNanos.set(0);
        minDurationNanos.reset();
        maxDurationNanos.reset();

        startTimeMillis = System.currentTimeMillis();

        switch (mode) {
            case LOAD -> startLoadTest(idOffset);
            case STRESS -> startStressTest(idOffset);
        }
    }

    private void startLoadTest(int idOffset) throws InterruptedException {
        int totalClients = config.getClientNumber();
        int rps = config.getRequestPerSecond();
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(totalClients);

        log.info("Load test: {} clients at {} rps, lease={}s, renewBefore={}s",
                totalClients, rps,
                config.getLeaseDuration().getSeconds(), config.getRenewBefore());

        for (int i = 0; i < totalClients; i += rps) {
            int batchEnd = Math.min(i + rps, totalClients);
            for (int j = i; j < batchEnd; j++) {
                UUID clientId = deterministicId(idOffset + j);
                launchClient(executor, latch, clientId);
            }
            if (batchEnd < totalClients) {
                Thread.sleep(1000L);
            }
        }

        waitForCompletion(latch, totalClients, executor);
        printReport(currentMode, totalClients);
    }

    private void startStressTest(int idOffset) throws InterruptedException {
        int totalClients = config.getClientNumber();
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(totalClients);

        log.info("Stress test: {} clients submitted simultaneously, lease={}s",
                totalClients, config.getLeaseDuration().getSeconds());

        for (int i = 0; i < totalClients; i++) {
            UUID clientId = deterministicId(idOffset + i);
            launchClient(executor, latch, clientId);
        }

        waitForCompletion(latch, totalClients, executor);
        printReport(currentMode, totalClients);
    }

    private boolean launchClient(ExecutorService executor, CountDownLatch latch, UUID clientId) {
        ClientService client = new ClientService(
                clientId,
                config.getSecret(),
                "localhost",
                config.getServerPort(),
                config.getMaxRetry(),
                config.getLeaseDuration(),
                config.getRenewBefore()
        );
        clients.add(client);

        executor.submit(() -> {
            try {
                client.register();
                successCount.incrementAndGet();
                long dur = client.getRegisterDurationNanos();
                totalDurationNanos.addAndGet(dur);
                minDurationNanos.accumulate(dur);
                maxDurationNanos.accumulate(dur);
                totalAttempts.addAndGet(client.getAttemptCount());
                if (client.isTimedOut()) {
                    timeoutCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
                totalAttempts.addAndGet(client.getAttemptCount());
                if (client.isTimedOut()) {
                    timeoutCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });
        return true;
    }

    private void waitForCompletion(CountDownLatch latch, int totalClients, ExecutorService executor) throws InterruptedException {
        long totalWait = 120_000L;
        boolean allDone = latch.await(totalWait, TimeUnit.MILLISECONDS);
        if (allDone) {
            log.info("All done: {} succeeded, {} failed", successCount.get(), failCount.get());
        } else {
            log.warn("Timed out after {}s: {} succeeded, {} failed out of {}",
                    totalWait / 1000, successCount.get(), failCount.get(), totalClients);
        }
        executor.shutdown();
        stop();
    }

    public static UUID deterministicId(int index) {
        return new UUID(0, index + 1);
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailCount() {
        return failCount.get();
    }

    public void stop() {
        for (ClientService client : clients) {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void printReport(Mode mode, int totalClients) {
        long elapsedMs = System.currentTimeMillis() - startTimeMillis;
        int success = successCount.get();
        int fail = failCount.get();
        int timeouts = timeoutCount.get();
        int attempts = totalAttempts.get();
        int retries = attempts > 0 ? attempts - totalClients : 0;
        long minDur = minDurationNanos.get();
        long maxDur = maxDurationNanos.get();

        double successRate = totalClients > 0 ? (success * 100.0 / totalClients) : 0;
        double actualRps = elapsedMs > 0 ? (success * 1000.0 / elapsedMs) : 0;
        double avgDurMs = success > 0 ? (totalDurationNanos.get() / 1_000_000.0 / success) : 0;
        double minDurMs = minDur != Long.MAX_VALUE ? minDur / 1_000_000.0 : 0;
        double maxDurMs = maxDur != Long.MIN_VALUE ? maxDur / 1_000_000.0 : 0;

        String report = String.format("""
                ===== %s TEST REPORT =====
                Simulated clients:                  %d
                Configured rate:                    %d rps
                Actual throughput:                  %.1f rps
                Total registration procedures:      %d
                Successful registrations:           %d
                Failed registrations:               %d
                Timeout requests:                   %d
                Retries:                            %d
                Response time avg / min / max:      %.1f / %.1f / %.1f ms
                Registration success rate:          %.1f%%
                Total run time:                     %d ms
                """, mode, totalClients, config.getRequestPerSecond(), actualRps,
                attempts, success, fail, timeouts, retries, avgDurMs, minDurMs, maxDurMs, successRate, elapsedMs);
        log.info(report);
        System.out.println(report);
    }
}
