package com.kinich.settings;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Enlace a una red social de la instalación. Se aplica al guardar, como el resto de la configuración. */
@Getter
@Entity
@Table(name = "social_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialLink extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialPlatform platform;

    @Column(length = 60)
    private String label;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private int position;

    @Version
    @Column(nullable = false)
    private long version;

    SocialLink(SocialLinkValues values, int position) {
        this.position = position;
        edit(values);
    }

    void edit(SocialLinkValues values) {
        this.platform = values.platform();
        this.label = values.label();
        this.url = values.url();
        this.visible = values.visible();
    }

    void moveTo(int position) {
        this.position = position;
    }
}
