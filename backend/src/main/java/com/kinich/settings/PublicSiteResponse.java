package com.kinich.settings;

import java.util.List;

/**
 * Identidad pública de la instalación. businessName es null mientras no se configure: el frontend muestra el
 * nombre provisional del producto (Kinich). Nunca contiene ids internos ni rutas: el logotipo es una URL opaca.
 */
public record PublicSiteResponse(String businessName, String tagline, LogoResponse logo, LogoResponse logoOnDark,
                                 ContactResponse contact, List<PublicSocialLink> socialLinks) {}
