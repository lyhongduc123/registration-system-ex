package org.lhduc.registration.repository;

import lombok.AllArgsConstructor;
import org.lhduc.registration.models.ClientCredential;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@AllArgsConstructor
public class RegistrationRepository {
    private final ConcurrentMap<UUID, ClientCredential> clientCredentials;

    public RegistrationRepository() {
        clientCredentials = new ConcurrentHashMap<>();
    }

    public boolean add(ClientCredential clientCredential) {
        return clientCredentials.putIfAbsent(clientCredential.getClientId(), clientCredential) == null;
    }

    public ClientCredential get(UUID clientId) {
        return clientCredentials.get(clientId);
    }

    public Collection<ClientCredential> getAll() {
        return clientCredentials.values();
    }

    public ClientCredential delete(UUID clientId) {
        return clientCredentials.remove(clientId);
    }
}
