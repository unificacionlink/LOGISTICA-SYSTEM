package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "usuario_rol")
@IdClass(UsuarioRolId.class)
@Getter
@Setter
@NoArgsConstructor
public class UsuarioRol {

    @Id
    @Column(name = "user_id")
    private UUID usuario;

    @Id
    @Column(name = "role_id")
    private UUID rol;

    @Id
    @Column(name = "company_id")
    private UUID empresa;

    public UsuarioRol(UUID usuario, UUID rol, UUID empresa) {
        this.usuario = usuario;
        this.rol = rol;
        this.empresa = empresa;
    }
}
