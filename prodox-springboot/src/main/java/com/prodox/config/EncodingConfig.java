// Autor: Cristian Santiago Martinez Cordoba - PRODOX
package com.prodox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Configuracion de encoding UTF-8 para caracteres espanoles.
 * 
 * Spring Boot 3.2.5 ya configura UTF-8 por defecto via propiedades:
 * - server.servlet.encoding.charset=UTF-8
 * - server.servlet.encoding.enabled=true
 * - server.servlet.encoding.force=true
 * 
 * Esta clase solo asegura que StringHttpMessageConverter use UTF-8
 * explicitamente para respuestas de texto plano.
 */
@Configuration
public class EncodingConfig implements WebMvcConfigurer {

    /**
     * Configura StringHttpMessageConverter para usar UTF-8.
     * NO reemplaza los converters existentes (como MappingJackson2HttpMessageConverter).
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
            .filter(converter -> converter instanceof StringHttpMessageConverter)
            .forEach(converter -> ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8));
    }
}