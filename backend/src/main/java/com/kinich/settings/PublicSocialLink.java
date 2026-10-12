package com.kinich.settings;

/** Red social visible en el sitio público: sin ids, versión ni orden internos (ya viene ordenada). */
public record PublicSocialLink(SocialPlatform platform, String label, String url) {}
