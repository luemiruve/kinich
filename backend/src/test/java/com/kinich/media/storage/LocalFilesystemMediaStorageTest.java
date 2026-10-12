package com.kinich.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemMediaStorageTest {

    @TempDir
    Path dir;

    private LocalFilesystemMediaStorage storage() {
        return new LocalFilesystemMediaStorage(new MediaProperties(dir));
    }

    @Test
    void storesUnderAServerGeneratedUuidAndReadsBack() throws Exception {
        String key = storage().store(new byte[] {1, 2, 3});
        assertThat(UUID.fromString(key).toString()).isEqualTo(key);
        assertThat(Files.list(dir).map(p -> p.getFileName().toString()).toList()).containsExactly(key);
        try (var in = storage().open(key)) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void leavesNoTemporaryFiles() throws Exception {
        storage().store(new byte[] {9});
        assertThat(Files.list(dir).filter(p -> p.toString().endsWith(".tmp")).count()).isZero();
    }

    @Test
    void rejectsKeysThatAreNotCanonicalUuids() {
        for (String key : new String[] {"../etc/passwd", "..", "/tmp/x", "a/b", UUID.randomUUID().toString().toUpperCase(), ""}) {
            assertThatThrownBy(() -> storage().open(key)).as(key).isInstanceOf(MediaStorageException.class);
            assertThatThrownBy(() -> storage().delete(key)).as(key).isInstanceOf(MediaStorageException.class);
        }
    }

    @Test
    void errorsNeverContainPaths() {
        assertThatThrownBy(() -> storage().open(UUID.randomUUID().toString()))
                .isInstanceOf(MediaStorageException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(dir.toString());
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void deleteIsIdempotent() {
        String key = storage().store(new byte[] {1});
        storage().delete(key);
        storage().delete(key);
        assertThat(dir.resolve(key)).doesNotExist();
    }

    @Test
    void failsFastOnMissingRelativeOrUnwritableDirectory() {
        assertThatThrownBy(() -> new LocalFilesystemMediaStorage(new MediaProperties(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalFilesystemMediaStorage(new MediaProperties(Path.of("relativo"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalFilesystemMediaStorage(new MediaProperties(dir.resolve("no-existe"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
