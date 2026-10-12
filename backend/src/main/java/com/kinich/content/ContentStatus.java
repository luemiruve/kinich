package com.kinich.content;

/** Estado de un bloque visto desde administración. Se deriva del borrador y la instantánea; no se persiste. */
public enum ContentStatus {
    DRAFT,
    PUBLISHED,
    HIDDEN
}
