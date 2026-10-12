package com.kinich.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Medios v2 insertados por JDBC (sin archivos físicos) para pruebas que no ejercitan la subida. */
public final class MediaTestData {

    private MediaTestData() {
    }

    /** Asset READY con su original y una representación web por formato a 480 px. */
    public static UUID readyImage(JdbcTemplate jdbc, int width, int height) {
        UUID asset = UUID.randomUUID();
        jdbc.update("INSERT INTO media_assets (id, media_type, status, content_type, size_bytes, width, height, sha256, "
                + "created_at, updated_at) VALUES (?, 'IMAGE', 'READY', 'image/jpeg', 10, ?, ?, ?, now(), now())",
                asset, width, height, "0".repeat(64));
        file(jdbc, asset, "ORIGINAL", "image/jpeg", width, height);
        int webWidth = Math.min(480, width);
        int webHeight = Math.max(1, Math.round(height * webWidth / (float) width));
        file(jdbc, asset, "WEB", "image/avif", webWidth, webHeight);
        file(jdbc, asset, "WEB", "image/jpeg", webWidth, webHeight);
        return asset;
    }

    private static void file(JdbcTemplate jdbc, UUID asset, String role, String type, int width, int height) {
        jdbc.update("INSERT INTO media_files (id, media_asset_id, role, content_type, width, height, size_bytes, "
                + "storage_key, created_at) VALUES (?, ?, ?, ?, ?, ?, 10, ?, now())",
                UUID.randomUUID(), asset, role, type, width, height, UUID.randomUUID().toString());
    }
}
