package com.kinich.settings;

import com.kinich.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Solo HTTP ↔ DTO. Validación, versiones y transacciones viven en los servicios. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
class AdminSettingsController {

    private final SiteSettingsService settings;
    private final SocialLinkService links;
    private final SiteLogoService logos;
    private final SiteSettingsReader reader;

    @GetMapping
    AdminSettingsResponse get() {
        return reader.forAdmin();
    }

    @PutMapping("/identity")
    AdminSettingsResponse updateIdentity(@Valid @RequestBody IdentityUpdateRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return reader.forAdmin(settings.updateIdentity(request.businessName(), request.tagline(), request.version(),
                user.id()));
    }

    /** Sube el SVG de una variante (primary | on-dark). El nombre y el Content-Type del cliente se ignoran. */
    @PutMapping(path = "/logos/{variant}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AdminSettingsResponse replaceLogo(@PathVariable String variant, @RequestParam("file") MultipartFile file,
                                      @RequestParam long version, @AuthenticationPrincipal AuthenticatedUser user)
            throws IOException {
        return reader.forAdmin(logos.replace(LogoVariant.fromSlug(variant), file.getBytes(), version, user.id()));
    }

    @DeleteMapping("/logos/{variant}")
    AdminSettingsResponse removeLogo(@PathVariable String variant, @RequestParam long version,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return reader.forAdmin(logos.remove(LogoVariant.fromSlug(variant), version, user.id()));
    }

    @PutMapping("/contact")
    AdminSettingsResponse updateContact(@Valid @RequestBody ContactUpdateRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return reader.forAdmin(settings.updateContact(request.email(), request.whatsapp(), request.phone(),
                request.location(), request.address(), request.openingHours(), request.version(), user.id()));
    }

    @PostMapping("/social-links")
    @ResponseStatus(HttpStatus.CREATED)
    SocialLinkResponse createLink(@Valid @RequestBody SocialLinkCreateRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return SocialLinkResponse.from(links.create(request.platform(), request.label(), request.url(),
                request.visible(), user.id()));
    }

    @PutMapping("/social-links/{id}")
    SocialLinkResponse updateLink(@PathVariable UUID id, @Valid @RequestBody SocialLinkUpdateRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return SocialLinkResponse.from(links.update(id, request.platform(), request.label(), request.url(),
                request.visible(), request.version(), user.id()));
    }

    @DeleteMapping("/social-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLink(@PathVariable UUID id, @RequestParam long version,
                    @AuthenticationPrincipal AuthenticatedUser user) {
        links.delete(id, version, user.id());
    }

    @PutMapping("/social-links/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderLinks(@Valid @RequestBody SocialLinkOrderRequest request,
                      @AuthenticationPrincipal AuthenticatedUser user) {
        Map<UUID, Long> versions = new LinkedHashMap<>();
        request.items().forEach(item -> versions.put(item.id(), item.version()));
        links.reorder(versions, request.items().stream().map(SocialLinkOrderRequest.Item::id).toList(), user.id());
    }
}
