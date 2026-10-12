package com.kinich.media.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** kinich.media.storage-dir ← KINICH_MEDIA_DIR: ruta absoluta fuera del webroot y del repositorio. */
@ConfigurationProperties("kinich.media")
public record MediaProperties(Path storageDir) {}
