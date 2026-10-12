package com.kinich.media.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador de filesystem local. Nombre físico = UUID generado aquí, sin extensión; el nombre original
 * nunca llega a este nivel. Escritura atómica y duradera (temporal 0600 + force + ATOMIC_MOVE + sync del
 * directorio) y doble defensa contra path traversal. Las excepciones no encadenan la IOException (lleva rutas).
 */
@Component
public class LocalFilesystemMediaStorage implements MediaStorage {

    private final Path base;

    public LocalFilesystemMediaStorage(MediaProperties properties) {
        Path dir = properties.storageDir();
        if (dir == null || !dir.isAbsolute()) {
            throw new IllegalStateException("kinich.media.storage-dir (KINICH_MEDIA_DIR) debe ser una ruta absoluta");
        }
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
            throw new IllegalStateException("kinich.media.storage-dir (KINICH_MEDIA_DIR) no existe o no se puede escribir");
        }
        try {
            this.base = dir.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("kinich.media.storage-dir (KINICH_MEDIA_DIR) no es accesible");
        }
    }

    @Override
    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        Path temp = null;
        try {
            temp = Files.createTempFile(base, "upload-", ".tmp"); // POSIX: permisos 0600
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true); // durable antes de hacerse visible
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            syncDirectory();
            return key;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new MediaStorageException("guardar");
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (NoSuchFileException e) {
            throw new MediaStorageException("encontrar");
        } catch (IOException e) {
            throw new MediaStorageException("leer");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new MediaStorageException("eliminar");
        }
    }

    private Path resolve(String key) {
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) {
                throw new MediaStorageException("localizar");
            }
        } catch (IllegalArgumentException e) {
            throw new MediaStorageException("localizar");
        }
        Path path = base.resolve(key).normalize();
        if (!base.equals(path.getParent())) {
            throw new MediaStorageException("localizar");
        }
        return path;
    }

    /** Hace duradera la entrada del directorio tras el move. No todos los sistemas permiten abrir un directorio. */
    private void syncDirectory() {
        try (FileChannel directory = FileChannel.open(base, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Mejor esfuerzo: el contenido ya se sincronizó con force(true).
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // El temporal residual es un huérfano físico conocido y acotado.
        }
    }
}
