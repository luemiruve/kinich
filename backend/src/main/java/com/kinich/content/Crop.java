package com.kinich.content;

/** Rectángulo de encuadre normalizado (0–1) sobre el original. Pertenece al ContentItem, nunca al medio. */
public record Crop(double x, double y, double width, double height) {}
