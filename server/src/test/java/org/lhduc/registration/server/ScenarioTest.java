package org.lhduc.registration.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lhduc.registration.client.ClientSimulator;
import org.lhduc.registration.config.ClientConfig;
import org.lhduc.registration.config.ServerConfig;
import org.lhduc.registration.packet.ChallengePacket;
import org.lhduc.registration.packet.ErrorPacket;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.protocol.StatusCode;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioTest {

    private static final String SECRET = "testSecret";
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration CHALLENGE_TIMEOUT = Duration.ofSeconds(2);
    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        port = 9999;
        ServerConfig config = ServerConfig.builder()
                .port(port)
                .leaseDuration(LEASE)
                .challengeTimeout(CHALLENGE_TIMEOUT)
                .maxRetry(3)
                .clientCount(0)
                .secret(SECRET)
                .backlog(1000)
                .build();
        server = new Server(config);
        new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(500);
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void wrongHash() {
        assertThrows(TestClient.AuthFailedException.class, () -> {
            try (TestClient client = new TestClient("localhost", port, SECRET)) {
                client.registerExpectingError();
            }
        });
    }

    @Test
    void challengeReuse() throws IOException {
        UUID clientId = UUID.randomUUID();
        UUID challengeId;
        byte[] nonce;

        try (TestClient client = new TestClient(clientId, "localhost", port, SECRET)) {
            ChallengePacket challenge = client.sendRegisterOnly();
            challengeId = challenge.getChallengeId();
            nonce = challenge.getNonce();
            Packet result = client.sendChallengeResponse(challengeId, nonce, SECRET);
            assertEquals(MessageType.SUCCESS, result.getHeader().getType());
        }

        try (TestClient client = new TestClient(clientId, "localhost", port, SECRET)) {
            Packet result = client.sendChallengeResponse(challengeId, nonce, SECRET);
            assertEquals(MessageType.ERROR, result.getHeader().getType());
            assertEquals(StatusCode.INVALID_CHALLENGE, ((ErrorPacket) result).getStatusCode());
        }
    }

    @Test
    void expiredChallenge() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID challengeId;
        byte[] nonce;

        try (TestClient client = new TestClient(clientId, "localhost", port, SECRET)) {
            ChallengePacket challenge = client.sendRegisterOnly();
            challengeId = challenge.getChallengeId();
            nonce = challenge.getNonce();
        }

        Thread.sleep(CHALLENGE_TIMEOUT.toMillis() + 500);

        try (TestClient client = new TestClient(clientId, "localhost", port, SECRET)) {
            Packet result = client.sendChallengeResponse(challengeId, nonce, SECRET);
            assertEquals(MessageType.ERROR, result.getHeader().getType());
            assertEquals(StatusCode.INVALID_CHALLENGE, ((ErrorPacket) result).getStatusCode());
        }
    }

    @Test
    void noRenew() throws Exception {
        Duration shortLease = Duration.ofSeconds(3);
        int shortLeasePort = ++port;
        ServerConfig config = ServerConfig.builder()
                .port(shortLeasePort)
                .leaseDuration(shortLease)
                .challengeTimeout(CHALLENGE_TIMEOUT)
                .maxRetry(3)
                .clientCount(0)
                .secret(SECRET)
                .backlog(1000)
                .build();
        Server shortLeaseServer = new Server(config);
        new Thread(() -> {
            try {
                shortLeaseServer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(500);

        try {
            try (TestClient client = new TestClient("localhost", shortLeasePort, SECRET)) {
                client.register();
                Thread.sleep(shortLease.toMillis() + 2000);
                IOException error = assertThrows(IOException.class, client::renew);
                assertTrue(error.getMessage().contains("LEASE_EXPIRED"), error.getMessage());
            }
        } finally {
            shortLeaseServer.stop();
        }
    }

    @Test
    void deregisterRegistered() throws IOException {
        try (TestClient client = new TestClient("localhost", port, SECRET)) {
            client.register();
            assertTrue(client.isRegistered());
            client.deregister();
            assertFalse(client.isRegistered());
        }
    }

    @Test
    void deregisterNotRegistered() {
        assertThrows(IOException.class, () -> {
            try (TestClient client = new TestClient("localhost", port, SECRET)) {
                assertFalse(client.isRegistered());
                client.deregister();
            }
        });
    }

    @Test
    void deregisterTwice() throws IOException {
        try (TestClient client = new TestClient("localhost", port, SECRET)) {
            client.register();
            assertTrue(client.isRegistered());
            client.deregister();
            assertFalse(client.isRegistered());
            assertThrows(IOException.class, client::deregister);
        }
    }

    @Test
    void deregisterThenReRegister() throws IOException {
        TestClient client = new TestClient("localhost", port, SECRET);
        try {
            System.out.println("=== Step 1: register ===");
            client.register();
            System.out.println("=== Registered, sessionId=" + client.getSessionId() + " ===");
            assertTrue(client.isRegistered());
            System.out.println("=== Step 2: deregister ===");
            client.deregister();
            System.out.println("=== Deregistered ===");
            assertFalse(client.isRegistered());
            System.out.println("=== Step 3: register again ===");
            client.register();
            System.out.println("=== Registered again ===");
            assertTrue(client.isRegistered());
        } finally {
            System.out.println("=== Closing ===");
            client.close();
        }
    }

    @Test
    void deregisterThenRenew() throws IOException {
        try (TestClient client = new TestClient("localhost", port, SECRET)) {
            client.register();
            client.deregister();
            assertFalse(client.isRegistered());
            IOException error = assertThrows(IOException.class, client::renew);
            assertTrue(error.getMessage().contains("LEASE_EXPIRED"), error.getMessage());
        }
    }

    @Test
    void concurrentRegistration() throws Exception {
        int clientCount = 1000;

        ClientConfig clientConfig = ClientConfig.builder()
                .serverPort(port)
                .clientNumber(clientCount)
                .requestPerSecond(clientCount)
                .renewBefore(10)
                .maxRetry(3)
                .secret(SECRET)
                .leaseDuration(LEASE)
                .build();

        ClientSimulator simulator = new ClientSimulator(clientConfig);
        simulator.start(ClientSimulator.Mode.STRESS);

        assertTrue(simulator.getSuccessCount() >= clientCount - 10,
                "At least " + (clientCount - 10) + " should succeed, got " + simulator.getSuccessCount());
        assertEquals(0, simulator.getFailCount());
    }
}
