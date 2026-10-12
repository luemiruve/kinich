package com.kinich.settings;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Configuración de la instalación: identidad y contacto del negocio. Fila única creada por la migración V8.
 * Se aplica al guardar (sin borrador ni publicación). No es un almacén genérico: solo estos campos.
 */
@Getter
@Entity
@Table(name = "business_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessSettings extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "business_name", length = 120)
    private String businessName;

    @Column(length = 200)
    private String tagline;

    /** Recurso de marca SVG (V10). Obligatorio para considerar configurada la identidad. */
    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    /** Variante opcional para fondo oscuro; la base de datos exige que exista el principal. */
    @Column(name = "logo_on_dark_asset_id")
    private UUID logoOnDarkAssetId;

    @Column(length = 254)
    private String email;

    @Column(length = 16)
    private String whatsapp;

    @Column(length = 16)
    private String phone;

    @Column(length = 120)
    private String location;

    @Column(length = 300)
    private String address;

    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    @Version
    @Column(nullable = false)
    private long version;

    void changeIdentity(IdentityValues values) {
        this.businessName = values.businessName();
        this.tagline = values.tagline();
    }

    /** Asigna (o retira, con null) el recurso de una variante y devuelve el que tenía antes. */
    UUID changeLogo(LogoVariant variant, UUID assetId) {
        UUID previous = logoOf(variant);
        if (variant == LogoVariant.PRIMARY) {
            this.logoAssetId = assetId;
        } else {
            this.logoOnDarkAssetId = assetId;
        }
        return previous;
    }

    UUID logoOf(LogoVariant variant) {
        return variant == LogoVariant.PRIMARY ? logoAssetId : logoOnDarkAssetId;
    }

    void changeContact(ContactValues values) {
        this.email = values.email();
        this.whatsapp = values.whatsapp();
        this.phone = values.phone();
        this.location = values.location();
        this.address = values.address();
        this.openingHours = values.openingHours();
    }

    /**
     * La identidad está configurada cuando la instalación tiene nombre y logotipo principal. Mientras tanto el sitio
     * usa el nombre (o el provisional del producto) en texto y el panel lo avisa.
     */
    public boolean isIdentityConfigured() {
        return businessName != null && logoAssetId != null;
    }
}
