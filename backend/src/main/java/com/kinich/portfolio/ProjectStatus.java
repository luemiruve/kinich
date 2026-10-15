package com.kinich.portfolio;

/**
 * Estado derivado de un proyecto: con instantánea → PUBLISHED (aunque el borrador esté oculto, sigue público hasta
 * publicarlo, D-16); sin instantánea y oculto → HIDDEN; si no → DRAFT.
 */
public enum ProjectStatus {
    DRAFT, PUBLISHED, HIDDEN
}
