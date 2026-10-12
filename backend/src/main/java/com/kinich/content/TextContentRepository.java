package com.kinich.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextContentRepository extends JpaRepository<TextContent, UUID> {

    List<TextContent> findBySectionIdOrderByPosition(UUID sectionId);

    List<TextContent> findBySectionIdInOrderByPosition(Collection<UUID> sectionIds);

    /** Búsqueda por id Y sección: un id de otra sección no se encuentra (protección IDOR/BOLA). */
    Optional<TextContent> findByIdAndSectionId(UUID id, UUID sectionId);

    long countBySectionId(UUID sectionId);
}
