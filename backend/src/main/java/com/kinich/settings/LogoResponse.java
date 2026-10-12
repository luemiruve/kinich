package com.kinich.settings;

/**
 * Logotipo listo para &lt;img&gt;: URL pública opaca del recurso de marca y proporción de su viewBox (el frontend fija
 * ancho y alto sin saltos de layout). Igual en el panel y en el sitio público; nunca rutas de almacenamiento.
 */
public record LogoResponse(String url, double width, double height) {}
