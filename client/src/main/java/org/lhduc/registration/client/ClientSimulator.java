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
        int totalClients = config.getClientNumber();
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(totalClients, Runtime.getRuntime().availableProcessors() * 2)
        );
        CountDownLatch latch = new CountDownLatch(totalClients);

        log.info("Starting simulation: {} clients at {} rps, lease={}s, renewBefore={}s",
                totalClients, config.getRequestPerSecond(),
                config.getLeaseDuration().getSeconds(), config.getRenewBefore());

        long intervalMs = 1000L / config.getRequestPerSecond();

        for (int i = 0; i < totalClients; i++) {
            UUID clientId = deterministicId(i);
            ClientService client;
            try {
                client = new ClientService(
                        clientId,
                        config.getSecret(),
                        "localhost",
                        config.getServerPort(),
                        config.getMaxRetry(),
                        config.getLeaseDuration(),
                        config.getRenewBefore()
                );
                clients.add(client);
            } catch (Exception e) {
                log.error("Failed to create client {}: {}", clientId, e.getMessage());
                failCount.incrementAndGet();
                latch.countDown();
                continue;
            }

            ClientService finalClient = client;
            executor.submit(() -> {
                try {
                    finalClient.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });

            if (intervalMs > 0 && i < totalClients - 1) {
                Thread.sleep(intervalMs);
            }
        }

        log.info("All {} clients started. Waiting for registration...", totalClients);
        latch.await(30, TimeUnit.SECONDS);
        log.info("Registration complete: {} succeeded, {} failed out of {}",
                successCount.get(), failCount.get(), totalClients);
    }

    public void await(long duration, TimeUnit unit) throws InterruptedException {
        log.info("Simulation running for {}{}...", duration, unit);
        Thread.sleep(unit.toMillis(duration));
    }

    public static UUID deterministicId(int index) {
        return new UUID(0, index + 1);
    }

    public void stop() {
        clients.forEach(ClientService::stop);
        log.info("Simulation stopped. Final stats: {} succeeded, {} failed",
                successCount.get(), failCount.get());
    }
}