package org.lhduc.registration;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.client.ClientSimulator;
import org.lhduc.registration.config.ClientConfig;
import org.lhduc.registration.config.ConfigLoader;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException {
        ClientConfig config = ConfigLoader.load();
        log.info("Client config loaded: {} clients, {} rps, {}s lease, {}s renewBefore, secret={}",
                config.getClientNumber(), config.getRequestPerSecond(),
                config.getLeaseDuration().getSeconds(), config.getRenewBefore(),
                config.getSecret());

        ClientSimulator simulator = new ClientSimulator(config);
        simulator.start();

        Runtime.getRuntime().addShutdownHook(new Thread(simulator::stop));
    }
}