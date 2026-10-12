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

/** Etiqueta contextual de un elemento (catálogo en BD: crece sin cambios de código). */
@Getter
@Entity
@Table(name = "content_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentCategory extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 60)
    private String label;

    public ContentCategory(String label) {
        this.label = label;
    }
}
