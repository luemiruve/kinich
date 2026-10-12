package com.kinich.media.processing;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * kinich.media.processing.*: parámetros técnicos (no reglas de negocio). La calidad y la compresión se ajustan aquí
 * sin tocar el código; los valores por defecto se documentan en docs/configuracion.md.
 */
@ConfigurationProperties("kinich.media.processing")
public record MediaProcessingProperties(String command, Path workDir, Duration timeout, Integer avifQuality,
                                        Integer jpegQuality, Integer maxConcurrent) {

    public MediaProcessingProperties {
        command = command == null || command.isBlank() ? "vips" : command;
        workDir = workDir == null ? Path.of(System.getProperty("java.io.tmpdir")) : workDir;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        avifQuality = avifQuality == null ? 50 : avifQuality;
        jpegQuality = jpegQuality == null ? 82 : jpegQuality;
        maxConcurrent = maxConcurrent == null ? 2 : maxConcurrent;
    }
}
