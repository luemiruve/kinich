package com.kinich.content;

/** Textos de cabecera de una sección (antetítulo, título y, si la sección lo admite, un texto breve). */
public record SectionHeading(String eyebrow, String title, String body) {

    boolean isEmpty() {
        return eyebrow == null && title == null && body == null;
    }
}
