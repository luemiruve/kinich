package com.kinich.settings;

import java.util.List;

/** Toda la configuración para el panel. {@code version} es la de identidad + contacto (misma fila). */
public record AdminSettingsResponse(boolean identityConfigured, IdentityResponse identity, ContactResponse contact,
                                    List<SocialLinkResponse> socialLinks, long version) {}
