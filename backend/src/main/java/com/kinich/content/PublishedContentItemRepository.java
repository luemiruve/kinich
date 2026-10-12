package com.kinich.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublishedContentItemRepository extends JpaRepository<PublishedContentItem, UUID> {

    List<PublishedContentItem> findBySectionIdInOrderByPosition(Collection<UUID> sectionIds);

    Optional<PublishedContentItem> findBySourceId(UUID sourceId);

    /** Sin clearAutomatically: la Section cargada sigue gestionada (y protegida por @Version). */
    @Modifying(flushAutomatically = true)
    @Query("delete from PublishedContentItem p where p.sectionId = :sectionId")
    int deleteBySectionId(@Param("sectionId") UUID sectionId);
}
