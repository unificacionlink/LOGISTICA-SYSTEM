package com.tdem.logistica.security;

import java.security.Principal;

/** name = usuario.userId (String), unico y conocido por ambos lados para convertAndSendToUser(). */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
