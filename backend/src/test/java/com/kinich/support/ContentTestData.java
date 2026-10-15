package com.kinich.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deja el contenido editorial (Inicio y cabeceras de Portafolio) como recién migrado. Llamar antes y después de cada
 * prueba de contenido.
 */
public final class ContentTestData {

    public static final UUID HERO_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000001");
    public static final UUID EDITORIAL_NOTE_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000002");
    public static final UUID DISCIPLINES_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000003");
    public static final UUID VALUES_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000004");
    public static final UUID FEATURED_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000005");
    public static final UUID CLOSING_CTA_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000006");
    public static final UUID PORTFOLIO_PAGE_HEADER_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000101");
    public static final UUID PORTFOLIO_CLOSING_CTA_ID = UUID.fromString("0d1e0000-0000-4000-8000-000000000102");

    private ContentTestData() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM published_portfolio_projects"); // cascada a sus imágenes
        jdbc.update("DELETE FROM portfolio_projects"); // cascada a sus imágenes
        jdbc.update("DELETE FROM published_content_items");
        jdbc.update("DELETE FROM content_items");
        jdbc.update("DELETE FROM content_categories");
        SettingsTestData.reset(jdbc); // brand_assets y la configuración referencian users
        jdbc.update("DELETE FROM media_files");
        jdbc.update("DELETE FROM media_assets");
        jdbc.update("DELETE FROM published_text_contents");
        jdbc.update("DELETE FROM text_contents");
        jdbc.update("UPDATE sections SET draft_revision = 0, published_revision = 0, published_at = NULL, "
                + "published_by = NULL, created_by = NULL, updated_by = NULL, heading_eyebrow = NULL, heading_title = NULL, "
                + "heading_body = NULL, published_heading_eyebrow = NULL, published_heading_title = NULL, "
                + "published_heading_body = NULL");
        MediaDirExtension.clean();
    }
}
