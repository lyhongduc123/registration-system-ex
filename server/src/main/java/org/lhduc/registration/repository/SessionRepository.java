package org.lhduc.registration.repository;

import org.lhduc.registration.models.ClientSession;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

public class SessionRepository {
    private final ConcurrentMap<UUID, ClientSession> sessions = new ConcurrentHashMap<>();

    public ClientSession add(ClientSession session) {
        sessions.putIfAbsent(session.getClientId(), session);
        return session;
    }

    public ClientSession get(UUID clientId) {
        return sessions.get(clientId);
    }

    public List<ClientSession> getAll() {
        return sessions.values().stream().toList();
    }

    public void delete(UUID clientId) {
        sessions.remove(clientId);
    }

    public ClientSession computeIfPresent(UUID clientId, UnaryOperator<ClientSession> fn) {
        return sessions.computeIfPresent(clientId, (id, session) -> fn.apply(session));
    }
}
