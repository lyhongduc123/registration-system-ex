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

    public ClientSimulator(ClientConfig config) {
        this.config = config;
        this.clients = new ArrayList<>();
        this.successCount = new AtomicInteger(0);
        this.failCount = new AtomicInteger(0);
    }

    public void start() throws InterruptedException {
        start(Mode.STRESS);
    }

    public void start(Mode mode) throws InterruptedException {
        switch (mode) {
            case LOAD -> startLoadTest();
            case STRESS -> startStressTest();
        }
    }

    private void startLoadTest() throws InterruptedException {
        int totalClients = config.getClientNumber();
        int rps = config.getRequestPerSecond();
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(totalClients);

        log.info("Load test: {} clients at {} rps, lease={}s, renewBefore={}s",
                totalClients, rps,
                config.getLeaseDuration().getSeconds(), config.getRenewBefore());

        for (int i = 0; i < totalClients; i += rps) {
            int batchEnd = Math.min(i + rps, totalClients);
            long batchStart = System.currentTimeMillis();

            for (int j = i; j < batchEnd; j++) {
                UUID clientId = deterministicId(j);
                launchClient(executor, latch, clientId);
            }

            long elapsed = System.currentTimeMillis() - batchStart;
            long sleepMs = 1000L - elapsed;
            if (sleepMs > 0 && batchEnd < totalClients) {
                Thread.sleep(sleepMs);
            }
        }

        waitForCompletion(latch, totalClients);
    }

    private void startStressTest() throws InterruptedException {
        int totalClients = config.getClientNumber();
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(totalClients);

        log.info("Stress test: {} clients submitted simultaneously, lease={}s",
                totalClients, config.getLeaseDuration().getSeconds());

        for (int i = 0; i < totalClients; i++) {
            UUID clientId = deterministicId(i);
            launchClient(executor, latch, clientId);
        }

        waitForCompletion(latch, totalClients);
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
            } catch (Exception e) {
                log.warn("Client {} failed: {}", clientId, e.getMessage());
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        return true;
    }

    private void waitForCompletion(CountDownLatch latch, int totalClients) throws InterruptedException {
        long totalWait = 120_000L;
        boolean allDone = latch.await(totalWait, TimeUnit.MILLISECONDS);
        if (allDone) {
            log.info("All done: {} succeeded, {} failed", successCount.get(), failCount.get());
        } else {
            log.warn("Timed out after {}s: {} succeeded, {} failed out of {}",
                    totalWait / 1000, successCount.get(), failCount.get(), totalClients);
        }
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
        log.info("Simulation stopped. Final stats: {} succeeded, {} failed",
                successCount.get(), failCount.get());
    }
}