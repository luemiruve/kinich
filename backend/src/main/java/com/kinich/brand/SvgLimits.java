package com.kinich.brand;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites técnicos del SVG de identidad (kinich.brand.svg, valores en application.yml). No son reglas de negocio:
 * acotan el trabajo del sanitizador y se ajustan sin tocar el código.
 */
@ConfigurationProperties("kinich.brand.svg")
public record SvgLimits(int maxBytes, int maxElements, int maxDepth) {}
