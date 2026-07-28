package org.lhduc.registration.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class ClientCredential {
    private UUID clientId;
    private int port;
    private String host;
    private String group;
    private String clientSecret;
}
