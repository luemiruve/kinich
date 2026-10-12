package com.kinich.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.media.image.ImageFixtures;
import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.error.ErrorCode;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaDirExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MediaServiceIntegrationTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Autowired private MediaService service;
    @Autowired private MediaAssetRepository assets;
    @Autowired private MediaFileRepository files;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    private long filesInStorage() throws Exception {
        try (var stored = Files.list(MediaDirExtension.DIR)) {
            return stored.count();
        }
    }

    private long leftoversInWorkDir() throws Exception {
        try (var entries = Files.list(MediaDirExtension.WORK_DIR)) {
            return entries.count();
        }
    }

    private List<MediaFile> filesOf(MediaAsset asset, MediaFileRole role) {
        return files.findByMediaAssetIdInAndRole(List.of(asset.getId()), role);
    }

    @Test
    void keepsThePrivateOriginalIntactAndGeneratesTheWebRenditions() throws Exception {
        byte[] withGps = ImageFixtures.withJpegSegment(ImageFixtures.jpeg(2000, 1250), 0xE1,
                ImageFixtures.exifApp1Payload(1, true));

        MediaAsset saved = service.upload(withGps, ACTOR);

        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getWidth()).isEqualTo(2000);
        assertThat(saved.getHeight()).isEqualTo(1250);
        assertThat(saved.getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(saved.getSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(withGps)));
        // Original: tal como se subió (incluido su EXIF), privado.
        MediaFile original = filesOf(saved, MediaFileRole.ORIGINAL).get(0);
        assertThat(Files.readAllBytes(MediaDirExtension.DIR.resolve(original.getStorageKey()))).isEqualTo(withGps);
        // Representaciones: 480/768/1280/1920 (2560 > original) × AVIF + JPEG, sin metadatos.
        List<MediaFile> web = filesOf(saved, MediaFileRole.WEB);
        assertThat(web).hasSize(8);
        assertThat(web).extracting(MediaFile::getContentType).containsOnly("image/avif", "image/jpeg");
        assertThat(web.stream().filter(f -> f.getContentType().equals("image/avif"))).extracting(MediaFile::getWidth)
                .containsExactlyInAnyOrder(480, 768, 1280, 1920);
        for (MediaFile file : web) {
            byte[] bytes = Files.readAllBytes(MediaDirExtension.DIR.resolve(file.getStorageKey()));
            assertThat(bytes).hasSize((int) file.getSizeBytes());
            assertThat(ImageFixtures.contains(bytes, ImageFixtures.GPS_MARKER)).isFalse();
        }
        assertThat(filesInStorage()).isEqualTo(9);
        assertThat(leftoversInWorkDir()).isZero();
    }

    @Test
    void rendersExposeOnlyWebFilesSortedByWidth() {
        MediaAsset saved = service.upload(ImageFixtures.png(1000, 800), ACTOR);
        MediaRenditions renditions = service.renditions(List.of(saved.getId()), MediaAudience.PUBLIC).get(saved.getId());
        assertThat(renditions.sources()).extracting(ImageSource::type).containsExactly("image/avif");
        assertThat(renditions.fallback().type()).isEqualTo("image/jpeg");
        assertThat(renditions.fallback().candidates()).extracting(ImageCandidate::width).containsExactly(480, 768);
        String original = filesOf(saved, MediaFileRole.ORIGINAL).get(0).getId().toString();
        assertThat(renditions.toString()).doesNotContain(original).contains("/api/public/media/files/");
    }

    @Test
    void aTransparentImageFallsBackToPngInsteadOfJpeg() {
        MediaAsset saved = service.upload(ImageFixtures.transparentPng(900, 300), ACTOR);
        MediaRenditions renditions = service.renditions(List.of(saved.getId()), MediaAudience.PUBLIC).get(saved.getId());
        assertThat(renditions.sources()).extracting(ImageSource::type).containsExactly("image/avif");
        assertThat(renditions.fallback().type()).isEqualTo("image/png");
        assertThat(renditions.fallback().candidates()).extracting(ImageCandidate::width).containsExactly(480, 768);
        assertThat(filesOf(saved, MediaFileRole.WEB)).extracting(MediaFile::getContentType).doesNotContain("image/jpeg");
    }

    @Test
    void rejectedUploadsLeaveNoFileAndNoRow() throws Exception {
        assertThatThrownBy(() -> service.upload("<svg/>".getBytes(StandardCharsets.UTF_8), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getFieldErrors()).containsKey("file");
                });
        assertThatThrownBy(() -> service.upload(new byte[0], ACTOR)).isInstanceOf(ApiProblemException.class);
        byte[] rotated = ImageFixtures.withJpegSegment(ImageFixtures.jpeg(8, 8), 0xE1, ImageFixtures.exifApp1Payload(6, false));
        assertThatThrownBy(() -> service.upload(rotated, ACTOR)).isInstanceOf(ApiProblemException.class);
        byte[] truncated = java.util.Arrays.copyOf(ImageFixtures.jpeg(64, 64), 200);
        assertThatThrownBy(() -> service.upload(truncated, ACTOR)).isInstanceOf(ApiProblemException.class);
        assertThat(filesInStorage()).isZero();
        assertThat(assets.count()).isZero();
        assertThat(leftoversInWorkDir()).isZero();
    }

    @Test
    void tooLargeIs413WithValidationFailed() {
        assertThatThrownBy(() -> service.upload(new byte[(int) MediaService.MAX_BYTES + 1], ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                });
    }

    @Test
    void aRolledBackUploadDeletesTheOriginalAndEveryRendition() throws Exception {
        transactions.executeWithoutResult(status -> {
            service.upload(ImageFixtures.png(800, 600), ACTOR);
            status.setRollbackOnly();
        });
        assertThat(filesInStorage()).isZero();
        assertThat(assets.count()).isZero();
    }

    @Test
    void logsNeitherPathsNorStorageKeys(CapturedOutput output) {
        MediaAsset saved = service.upload(ImageFixtures.png(4, 4), ACTOR);
        assertThatThrownBy(() -> service.upload("<svg/>".getBytes(StandardCharsets.UTF_8), ACTOR))
                .isInstanceOf(ApiProblemException.class);
        assertThat(output).contains("Imagen subida").contains("Subida rechazada");
        assertThat(output).doesNotContain(MediaDirExtension.DIR.toString()).doesNotContain(MediaDirExtension.WORK_DIR.toString());
        for (MediaFile file : files.findByMediaAssetIdInAndRole(List.of(saved.getId()), MediaFileRole.WEB)) {
            assertThat(output).doesNotContain(file.getStorageKey());
        }
    }

    @Test
    void draftOnlyRenditionsAreNotPublishedAndTheOriginalIsNeverServed() {
        MediaAsset saved = service.upload(ImageFixtures.png(4, 4), ACTOR);
        MediaFile web = filesOf(saved, MediaFileRole.WEB).get(0);
        MediaFile original = filesOf(saved, MediaFileRole.ORIGINAL).get(0);
        assertThatThrownBy(() -> service.openPublishedWebFile(web.getId()))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.openWebFileForAdmin(original.getId()))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.openWebFileForAdmin(web.getId()).id()).isEqualTo(web.getId());
    }

    @Test
    void aMissingFileIsA404WithoutPathInTheLogs(CapturedOutput output) throws Exception {
        MediaAsset saved = service.upload(ImageFixtures.png(4, 4), ACTOR);
        MediaFile web = filesOf(saved, MediaFileRole.WEB).get(0);
        Files.delete(MediaDirExtension.DIR.resolve(web.getStorageKey()));
        assertThatThrownBy(() -> service.openWebFileForAdmin(web.getId()))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(output).contains("Falta el archivo de un medio").doesNotContain(MediaDirExtension.DIR.toString());
    }

    @Test
    void listsNewestFirst() {
        MediaAsset first = service.upload(ImageFixtures.png(4, 4), ACTOR);
        MediaAsset second = service.upload(ImageFixtures.png(5, 5), ACTOR);
        assertThat(service.list()).extracting(MediaAsset::getId).containsExactly(second.getId(), first.getId());
    }
}
