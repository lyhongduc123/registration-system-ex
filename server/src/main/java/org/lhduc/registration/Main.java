package org.lhduc.registration;

import lombok.extern.slf4j.Slf4j;
import org.lhduc.registration.config.ConfigLoader;
import org.lhduc.registration.config.ServerConfig;

@Slf4j
public class Main {
    public static void main(String[] args) {
        ServerConfig config = ConfigLoader.load();
        log.info("Server config loaded: port={}, lease={}s, challenge={}s, clients={}",
                config.getPort(), config.getLeaseDuration().getSeconds(),
                config.getChallengeTimeout().getSeconds(), config.getClientCount());
    }
}