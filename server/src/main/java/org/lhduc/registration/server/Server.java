package org.lhduc.registration.server;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.codec.PacketCodec;
import org.lhduc.registration.codec.serialize.AckSerializer;
import org.lhduc.registration.codec.serialize.ChallengeResponseSerializer;
import org.lhduc.registration.codec.serialize.ChallengeSerializer;
import org.lhduc.registration.codec.serialize.DeregisterSerializer;
import org.lhduc.registration.codec.serialize.ErrorSerializer;
import org.lhduc.registration.codec.serialize.RegisterSerializer;
import org.lhduc.registration.codec.serialize.RenewAckSerializer;
import org.lhduc.registration.codec.serialize.RenewSerializer;
import org.lhduc.registration.codec.serialize.SuccessSerializer;
import org.lhduc.registration.config.ServerConfig;
import org.lhduc.registration.dispatcher.PacketDispatcher;
import org.lhduc.registration.handler.ChallengeResponseHandler;
import org.lhduc.registration.handler.DeregisterHandler;
import org.lhduc.registration.handler.RegisterHandler;
import org.lhduc.registration.handler.RenewHandler;
import org.lhduc.registration.network.Connection;
import org.lhduc.registration.packet.Packet;
import org.lhduc.registration.protocol.MessageType;
import org.lhduc.registration.repository.ChallengeRepository;
import org.lhduc.registration.repository.RegistrationRepository;
import org.lhduc.registration.repository.SessionRepository;
import org.lhduc.registration.service.RegistrationService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Server {

    private final ServerConfig config;
    private final RegistrationService registrationService;
    private final PacketCodec codec;
    private final PacketDispatcher dispatcher;
    private final ChallengeRepository challengeRepository;
    private final SessionRepository sessionRepository;
    private final SessionExpirySweeper sweeper;

    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private volatile boolean running;

    public Server(ServerConfig config) {
        this.config = config;
        this.challengeRepository = new ChallengeRepository();
        this.sessionRepository = new SessionRepository();
        this.registrationService = createService();
        this.sweeper = new SessionExpirySweeper(registrationService, Duration.ofSeconds(5));
        this.codec = createCodec();
        this.dispatcher = createDispatcher();
    }

    private RegistrationService createService() {
        RegistrationRepository regRepo = new RegistrationRepository();
        return new RegistrationService(regRepo, challengeRepository, sessionRepository, config);
    }

    private static PacketCodec createCodec() {
        PacketCodec codec = new PacketCodec();
        codec.registerSerializer(MessageType.REGISTER, new RegisterSerializer());
        codec.registerSerializer(MessageType.CHALLENGE, new ChallengeSerializer());
        codec.registerSerializer(MessageType.CHALLENGE_RESPONSE, new ChallengeResponseSerializer());
        codec.registerSerializer(MessageType.RENEW, new RenewSerializer());
        codec.registerSerializer(MessageType.RENEW_ACK, new RenewAckSerializer());
        codec.registerSerializer(MessageType.SUCCESS, new SuccessSerializer());
        codec.registerSerializer(MessageType.DEREGISTER, new DeregisterSerializer());
        codec.registerSerializer(MessageType.ACK, new AckSerializer());
        codec.registerSerializer(MessageType.ERROR, new ErrorSerializer());
        return codec;
    }

    private PacketDispatcher createDispatcher() {
        PacketDispatcher dispatcher = new PacketDispatcher();
        dispatcher.registerHandler(MessageType.REGISTER, new RegisterHandler(registrationService));
        dispatcher.registerHandler(MessageType.CHALLENGE_RESPONSE, new ChallengeResponseHandler(registrationService));
        dispatcher.registerHandler(MessageType.RENEW, new RenewHandler(registrationService));
        dispatcher.registerHandler(MessageType.DEREGISTER, new DeregisterHandler(registrationService));
        return dispatcher;
    }

    public void start() throws IOException {
        running = true;
        int backlog = config.getBacklog() > 0 ? config.getBacklog() : 50;
        serverSocket = new ServerSocket(config.getPort(), backlog);
        clientPool = Executors.newCachedThreadPool();
        sweeper.start();

        log.info("Server listening on port {}", config.getPort());

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                log.info("New connection from {}", socket.getRemoteSocketAddress());
                clientPool.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (running) {
                    log.error("Accept failed", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (Connection conn = new Connection(socket, codec)) {
            while (running) {
                Packet packet = conn.readPacket();
                dispatcher.dispatch(conn, packet);
            }
        } catch (IOException e) {
            log.debug("Connection closed: {}", e.getMessage());
        }
    }

    public void stop() {
        running = false;
        sweeper.stop();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing server socket", e);
        }
        if (clientPool != null) {
            clientPool.shutdown();
        }
        log.info("Server stopped");
    }
}