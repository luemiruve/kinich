package com.kinich.settings;

/** Enlace ya normalizado y validado (URL HTTPS absoluta). */
record SocialLinkValues(SocialPlatform platform, String label, String url, boolean visible) {}
