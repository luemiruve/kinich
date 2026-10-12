package com.kinich.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Las pruebas de integración usan un directorio de medios temporal propio, nunca KINICH_MEDIA_DIR. */
public class MediaDirExtension implements BeforeAllCallback {

    public static final Path DIR = Path.of(System.getProperty("java.io.tmpdir"), "kinich-media-test");
    /** Directorio de trabajo de libvips en pruebas: debe quedar vacío tras cada procesamiento. */
    public static final Path WORK_DIR = Path.of(System.getProperty("java.io.tmpdir"), "kinich-media-work-test");

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Files.createDirectories(DIR);
        Files.createDirectories(WORK_DIR);
    }

    /** Borra los archivos que dejaron las pruebas. Solo en el directorio temporal de pruebas. */
    public static void clean() {
        try (var files = Files.list(DIR)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
