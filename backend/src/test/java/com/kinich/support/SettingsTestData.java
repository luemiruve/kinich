package com.kinich.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Deja la configuración de la instalación como recién migrada: una fila vacía, sin redes ni logotipos. */
public final class SettingsTestData {

    private SettingsTestData() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM social_links");
        jdbc.update("UPDATE business_settings SET business_name = NULL, tagline = NULL, logo_on_dark_asset_id = NULL, "
                + "logo_asset_id = NULL, email = NULL, whatsapp = NULL, phone = NULL, location = NULL, "
                + "address = NULL, opening_hours = NULL, version = 0, created_by = NULL, updated_by = NULL");
        jdbc.update("DELETE FROM brand_assets");
    }
}
