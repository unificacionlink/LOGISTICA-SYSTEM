package com.tdem.logistica.config;

import com.tdem.logistica.repository.RepositorioBaseImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Todos los repositorios heredan {@code insertar} (DEC-L074). */
@Configuration
@EnableJpaRepositories(basePackages = "com.tdem.logistica.repository", repositoryBaseClass = RepositorioBaseImpl.class)
public class JpaConfig {
}
