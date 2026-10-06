package com.tdem.logistica.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UsuarioRolId implements Serializable {

    private UUID usuario;
    private UUID rol;
    private UUID empresa;

    public UsuarioRolId() {
    }

    public UsuarioRolId(UUID usuario, UUID rol, UUID empresa) {
        this.usuario = usuario;
        this.rol = rol;
        this.empresa = empresa;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsuarioRolId that)) return false;
        return Objects.equals(usuario, that.usuario) && Objects.equals(rol, that.rol) && Objects.equals(empresa, that.empresa);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuario, rol, empresa);
    }
}
