package com.kinich.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublishedTextContentRepository extends JpaRepository<PublishedTextContent, UUID> {

    List<PublishedTextContent> findBySectionIdInOrderByPosition(Collection<UUID> sectionIds);

    List<PublishedTextContent> findBySectionIdOrderByPosition(UUID sectionId);

    Optional<PublishedTextContent> findBySourceId(UUID sourceId);

    /** Sin clearAutomatically: la Section cargada en la misma transacción sigue gestionada (y protegida por @Version). */
    @Modifying(flushAutomatically = true)
    @Query("delete from PublishedTextContent p where p.sectionId = :sectionId")
    int deleteBySectionId(@Param("sectionId") UUID sectionId);
}
