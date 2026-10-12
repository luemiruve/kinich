package com.kinich.content;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bloque de texto en BORRADOR. Nunca lo lee la API pública. */
@Getter
@Entity
@Table(name = "text_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TextContent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(length = 120)
    private String eyebrow;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    private int position;

    @Version
    @Column(nullable = false)
    private long version;

    public TextContent(UUID sectionId, TextValues values, int position) {
        this.sectionId = sectionId;
        this.position = position;
        edit(values);
    }

    public void edit(TextValues values) {
        this.eyebrow = values.eyebrow();
        this.title = values.title();
        this.body = values.body();
    }

    public void changeVisibility(boolean hidden) {
        this.hidden = hidden;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public boolean hasSameContentAs(PublishedTextContent copy) {
        return Objects.equals(eyebrow, copy.getEyebrow()) && Objects.equals(title, copy.getTitle())
                && body.equals(copy.getBody());
    }
}
