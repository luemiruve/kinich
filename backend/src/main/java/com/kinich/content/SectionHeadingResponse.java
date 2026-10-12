package com.kinich.content;

/** Resultado de editar una cabecera: el borrador y el estado de la sección para el siguiente guardado. */
public record SectionHeadingResponse(SectionHeading heading, long revision, boolean hasUnpublishedChanges) {

    static SectionHeadingResponse from(Section section) {
        return new SectionHeadingResponse(section.draftHeading(), section.getDraftRevision(),
                section.hasUnpublishedChanges());
    }
}
