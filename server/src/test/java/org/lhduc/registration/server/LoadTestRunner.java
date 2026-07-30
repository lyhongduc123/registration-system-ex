package org.lhduc.registration.server;

import org.junit.jupiter.api.Test;
import org.lhduc.registration.client.ClientSimulator;
import org.lhduc.registration.config.ClientConfig;
import org.lhduc.registration.config.ServerConfig;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;

public class LoadTestRunner {

    private static final String SECRET = "testSecret";
    private static final Duration LEASE = Duration.ofSeconds(60);
    private static final Duration CHALLENGE_TIMEOUT = Duration.ofSeconds(5);
    private static final int PORT = 9998;

    private int nextIdOffset = 0;

    @Test
    void runAllScenarios() throws Exception {
        System.out.println("Starting server on port " + PORT);
        Server server = startServer();
        try {
            Thread.sleep(1000);

            runScenario(100, 100, "Stress - 100 clients, 100 rps");
            runScenario(500, 500, "Stress - 500 clients, 500 rps");
            runScenario(1000, 1000, "Stress - 1000 clients, 1000 rps");

            runScenario(100, 10, "Load - 100 clients, 10 rps");
            runScenario(500, 50, "Load - 500 clients, 50 rps");
            runScenario(1000, 100, "Load - 1000 clients, 100 rps");

        } finally {
            server.stop();
            System.out.println("Server stopped.");
        }
    }

    private static Server startServer() throws IOException {
        ServerConfig config = ServerConfig.builder()
                .port(PORT)
                .leaseDuration(LEASE)
                .challengeTimeout(CHALLENGE_TIMEOUT)
                .maxRetry(3)
                .clientCount(0)
                .secret(SECRET)
                .backlog(5000)
                .build();
        Server server = new Server(config);
        new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        return server;
    }

    private void runScenario(int clientCount, int rps, String label) throws InterruptedException {
        int offset = nextIdOffset;
        nextIdOffset += Math.max(clientCount, 100) + 1000;

        System.out.println("\n========== " + label + " ==========\n");

        ClientConfig clientConfig = ClientConfig.builder()
                .serverPort(PORT)
                .clientNumber(clientCount)
                .requestPerSecond(rps)
                .renewBefore(10)
                .maxRetry(3)
                .secret(SECRET)
                .leaseDuration(LEASE)
                .build();

        ClientSimulator.Mode mode = (rps >= clientCount) ? ClientSimulator.Mode.STRESS : ClientSimulator.Mode.LOAD;

        System.gc();
        Thread.sleep(200);
        long memBefore = getUsedMemory();
        long testStart = System.currentTimeMillis();

        ClientSimulator simulator = new ClientSimulator(clientConfig);
        simulator.start(mode, offset);

        long elapsed = System.currentTimeMillis() - testStart;
        long memAfter = getUsedMemory();

        int success = simulator.getSuccessCount();
        int fail = simulator.getFailCount();

        String cpuInfo = getCpuInfo();

        System.out.println("--- System Resources ---");
        System.out.println("RAM (before/after):        " + (memBefore / (1024 * 1024)) + " MB / " + (memAfter / (1024 * 1024)) + " MB");
        System.out.println("CPU:                        " + cpuInfo);
        System.out.println("Total run time:             " + elapsed + " ms");
        System.out.println("========================================\n");
    }

    private static String getCpuInfo() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double load = sunOsBean.getProcessCpuLoad();
                if (load >= 0) {
                    return String.format("%.1f%%", load * 100);
                }
                return "N/A (not available)";
            }
            return "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static long getUsedMemory() {
        try {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        } catch (Exception e) {
            return -1;
        }
    }
}
