package com.kinich.content;

import static com.kinich.support.ContentTestData.DISCIPLINES_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MediaSchemaIntegrationTest {

    private static final String SHA = "a".repeat(64);
    private static final String ITEM = "INSERT INTO content_items (id, section_id, media_asset_id, image_alt, position, "
            + "crop_x, crop_y, crop_width, crop_height, focal_x, focal_y, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    private UUID insertAsset(String contentType, long size, int width, int height) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO media_assets (id, media_type, status, content_type, size_bytes, width, height, sha256, "
                + "created_at, updated_at) VALUES (?, 'IMAGE', 'READY', ?, ?, ?, ?, ?, now(), now())",
                id, contentType, size, width, height, SHA);
        return id;
    }

    private void insertFile(UUID asset, String role, String contentType, int width) {
        jdbc.update("INSERT INTO media_files (id, media_asset_id, role, content_type, width, height, size_bytes, "
                + "storage_key, created_at) VALUES (?, ?, ?, ?, ?, 10, 10, ?, now())",
                UUID.randomUUID(), asset, role, contentType, width, UUID.randomUUID().toString());
    }

    private void insertItem(UUID asset, Double cropX, Double cropY, Double cropW, Double cropH, Double focalX,
                            Double focalY) {
        jdbc.update(ITEM, UUID.randomUUID(), DISCIPLINES_ID, asset, "alt", 1, cropX, cropY, cropW, cropH, focalX, focalY);
    }

    @Test
    void assetsAcceptTheFourOriginalFormatsWithinLimits() {
        for (String type : new String[] {"image/jpeg", "image/png", "image/webp", "image/avif"}) {
            insertAsset(type, 10_485_760L, 8000, 8000);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_assets", Integer.class)).isEqualTo(4);
    }

    @Test
    void assetsRejectOtherTypesSizesDimensionsAndBadHashes() {
        assertThatThrownBy(() -> insertAsset("image/svg+xml", 10, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAsset("image/png", 10_485_761L, 1, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAsset("image/png", 10, 8001, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO media_assets (id, media_type, status, content_type, size_bytes, "
                + "width, height, sha256, created_at, updated_at) VALUES (?, 'VIDEO', 'READY', 'image/png', 1, 1, 1, ?, now(), "
                + "now())", UUID.randomUUID(), SHA)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO media_assets (id, media_type, status, content_type, size_bytes, "
                + "width, height, sha256, created_at, updated_at) VALUES (?, 'IMAGE', 'READY', 'image/png', 1, 1, 1, 'x', now(), "
                + "now())", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAssetHasExactlyOneOriginal() {
        UUID asset = insertAsset("image/jpeg", 10, 4000, 3000);
        insertFile(asset, "ORIGINAL", "image/jpeg", 4000);
        assertThatThrownBy(() -> insertFile(asset, "ORIGINAL", "image/jpeg", 4000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void webRenditionsAreAvifJpegOrPngAtCandidateWidthsOnly() {
        UUID asset = insertAsset("image/png", 10, 4000, 3000);
        for (int width : new int[] {480, 768, 1280, 1920, 2560}) {
            insertFile(asset, "WEB", "image/avif", width);
            insertFile(asset, "WEB", "image/jpeg", width);
        }
        insertFile(insertAsset("image/png", 10, 300, 200), "WEB", "image/jpeg", 300); // original más estrecho que 480
        UUID transparent = insertAsset("image/png", 10, 900, 300);
        insertFile(transparent, "WEB", "image/png", 480); // respaldo PNG de una imagen con transparencia (V7)
        assertThatThrownBy(() -> insertFile(asset, "WEB", "image/webp", 480)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFile(transparent, "WEB", "image/png", 1000)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFile(asset, "WEB", "image/jpeg", 1000)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFile(asset, "WEB", "image/avif", 480)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referencedAssetsAndTheirFilesCannotBeDeleted() {
        UUID asset = insertAsset("image/png", 10, 1, 1);
        insertFile(asset, "ORIGINAL", "image/png", 1);
        insertItem(asset, null, null, null, null, 0.5, 0.5);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_assets WHERE id = ?", asset))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anItemHasExactlyOneFramingWithinBounds() {
        UUID asset = insertAsset("image/png", 10, 100, 100);
        insertItem(asset, 0.1, 0.0, 0.8, 1.0, null, null);
        insertItem(asset, null, null, null, null, 0.3, 0.7);
        assertThatThrownBy(() -> insertItem(asset, null, null, null, null, null, null)) // ninguno
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(asset, 0.1, 0.0, 0.8, 1.0, 0.5, 0.5)) // los dos
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(asset, 0.5, 0.0, 0.8, 1.0, null, null)) // se sale por la derecha
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(asset, 0.1, 0.0, null, 1.0, null, null)) // rectángulo incompleto
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(asset, null, null, null, null, 1.2, 0.5)) // foco fuera de rango
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void contentItemsRequireNonBlankAltAndPositivePosition() {
        UUID asset = insertAsset("image/png", 10, 1, 1);
        assertThatThrownBy(() -> jdbc.update(ITEM, UUID.randomUUID(), DISCIPLINES_ID, asset, " ", 1, null, null, null,
                null, 0.5, 0.5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(ITEM, UUID.randomUUID(), DISCIPLINES_ID, asset, "alt", 0, null, null, null,
                null, 0.5, 0.5)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishedPositionsAreUniquePerSectionAndCopiesSurviveDraftDeletion() {
        UUID asset = insertAsset("image/png", 10, 1, 1);
        UUID draft = UUID.randomUUID();
        jdbc.update(ITEM, draft, DISCIPLINES_ID, asset, "alt", 1, null, null, null, null, 0.5, 0.5);
        String published = "INSERT INTO published_content_items (id, section_id, source_id, media_asset_id, image_alt, "
                + "position, focal_x, focal_y, created_at, updated_at) VALUES (?, ?, ?, ?, 'alt', 1, 0.5, 0.5, now(), now())";
        jdbc.update(published, UUID.randomUUID(), DISCIPLINES_ID, draft, asset);
        assertThatThrownBy(() -> jdbc.update(published, UUID.randomUUID(), DISCIPLINES_ID, null, asset))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM content_items WHERE id = ?", draft);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM published_content_items WHERE source_id IS NULL",
                Integer.class)).isEqualTo(1);
    }
}
