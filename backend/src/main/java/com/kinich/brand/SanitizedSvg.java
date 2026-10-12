package com.kinich.brand;

/** SVG ya sanitizado y reescrito (lo único que se almacena) con la proporción de su viewBox. */
public record SanitizedSvg(byte[] content, double viewBoxWidth, double viewBoxHeight) {}
