package com.kinich.settings;

import com.kinich.brand.BrandAsset;
import com.kinich.brand.BrandAssetService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arma las respuestas de lectura (panel y sitio público). Los logotipos son recursos de marca SVG (V10). */
@Service
@RequiredArgsConstructor
public class SiteSettingsReader {

    static final String BRAND_ASSET_URL = "/api/public/brand-assets/";

    private final SiteSettingsService settings;
    private final SocialLinkService links;
    private final BrandAssetService brandAssets;

    @Transactional(readOnly = true)
    public AdminSettingsResponse forAdmin() {
        return forAdmin(settings.load());
    }

    @Transactional(readOnly = true)
    public AdminSettingsResponse forAdmin(BusinessSettings current) {
        Map<UUID, BrandAsset> logos = logos(current);
        IdentityResponse identity = new IdentityResponse(current.getBusinessName(), current.getTagline(),
                logo(current.getLogoAssetId(), logos), logo(current.getLogoOnDarkAssetId(), logos));
        return new AdminSettingsResponse(current.isIdentityConfigured(), identity, ContactResponse.from(current),
                links.list().stream().map(SocialLinkResponse::from).toList(), current.getVersion());
    }

    @Transactional(readOnly = true)
    public PublicSiteResponse forPublic() {
        BusinessSettings current = settings.load();
        Map<UUID, BrandAsset> logos = logos(current);
        return new PublicSiteResponse(current.getBusinessName(), current.getTagline(),
                logo(current.getLogoAssetId(), logos), logo(current.getLogoOnDarkAssetId(), logos),
                ContactResponse.from(current),
                links.listVisible().stream()
                        .map(link -> new PublicSocialLink(link.getPlatform(), link.getLabel(), link.getUrl())).toList());
    }

    private Map<UUID, BrandAsset> logos(BusinessSettings current) {
        return brandAssets.find(Stream.of(current.getLogoAssetId(), current.getLogoOnDarkAssetId())
                .filter(Objects::nonNull).toList());
    }

    private static LogoResponse logo(UUID id, Map<UUID, BrandAsset> logos) {
        BrandAsset asset = id == null ? null : logos.get(id);
        return asset == null ? null
                : new LogoResponse(BRAND_ASSET_URL + asset.getId(), asset.getViewBoxWidth(), asset.getViewBoxHeight());
    }
}
