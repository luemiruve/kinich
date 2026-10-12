package com.kinich.content;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sección del catálogo (solo se crea por migración). Cada cambio en su borrador incrementa draftRevision;
 * publicar iguala publishedRevision. @Version serializa las escrituras concurrentes sobre la sección.
 */
@Getter
@Entity
@Table(name = "sections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section extends AuditableEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_key", nullable = false, length = 40, updatable = false)
    private PageKey pageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, length = 60, updatable = false)
    private SectionKey sectionKey;

    @Column(nullable = false)
    private int position;

    @Column(name = "draft_revision", nullable = false)
    private long draftRevision;

    @Column(name = "published_revision", nullable = false)
    private long publishedRevision;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "heading_eyebrow", length = 120)
    private String headingEyebrow;

    @Column(name = "heading_title", length = 200)
    private String headingTitle;

    @Column(name = "heading_body", length = 600)
    private String headingBody;

    @Column(name = "published_heading_eyebrow", length = 120)
    private String publishedHeadingEyebrow;

    @Column(name = "published_heading_title", length = 200)
    private String publishedHeadingTitle;

    @Column(name = "published_heading_body", length = 600)
    private String publishedHeadingBody;

    @Version
    @Column(nullable = false)
    private long version;

    public void markDraftChanged() {
        draftRevision++;
    }

    public void editHeading(SectionHeading heading) {
        headingEyebrow = heading.eyebrow();
        headingTitle = heading.title();
        headingBody = heading.body();
        markDraftChanged();
    }

    public SectionHeading draftHeading() {
        return new SectionHeading(headingEyebrow, headingTitle, headingBody);
    }

    /** null si la sección no tiene cabecera publicada. */
    public SectionHeading publishedHeading() {
        SectionHeading heading = new SectionHeading(publishedHeadingEyebrow, publishedHeadingTitle, publishedHeadingBody);
        return heading.isEmpty() ? null : heading;
    }

    /** Publicar la sección fija también su cabecera. */
    public void markPublished(UUID by, Instant at) {
        publishedHeadingEyebrow = headingEyebrow;
        publishedHeadingTitle = headingTitle;
        publishedHeadingBody = headingBody;
        publishedRevision = draftRevision;
        publishedAt = at;
        publishedBy = by;
    }

    public boolean hasUnpublishedChanges() {
        return draftRevision != publishedRevision;
    }
}
