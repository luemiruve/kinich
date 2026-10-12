package com.kinich.content;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Copia inmutable de un bloque visible en el momento de publicar. Lo único que lee la API pública. */
@Getter
@Entity
@Table(name = "published_text_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishedTextContent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    /** Bloque del borrador que originó la copia; null si después se eliminó del borrador. */
    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(length = 120, updatable = false)
    private String eyebrow;

    @Column(length = 200, updatable = false)
    private String title;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private int position;

    public static PublishedTextContent snapshotOf(TextContent draft, int position) {
        PublishedTextContent copy = new PublishedTextContent();
        copy.sectionId = draft.getSectionId();
        copy.sourceId = draft.getId();
        copy.eyebrow = draft.getEyebrow();
        copy.title = draft.getTitle();
        copy.body = draft.getBody();
        copy.position = position;
        return copy;
    }
}
