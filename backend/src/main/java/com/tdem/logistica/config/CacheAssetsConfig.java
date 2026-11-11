package com.tdem.logistica.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Cache del navegador para el build de React. Vite pone un hash en el nombre de
 * cada archivo de /assets (index-DIGykInw.js): si el contenido cambia, cambia el
 * nombre, asi que se pueden guardar un anio sin riesgo. index.html no entra aqui
 * (sigue con no-store de Spring Security) para que cada deploy llegue al instante.
 * La API tampoco: sus respuestas son privadas y siguen con no-store.
 *
 * Spring Security no pisa este header: solo escribe Cache-Control si la
 * respuesta todavia no tiene uno.
 */
@Configuration
public class CacheAssetsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/favicon.svg")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
    }
}
