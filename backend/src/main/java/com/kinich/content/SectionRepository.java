package com.kinich.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findByPageKeyOrderByPosition(PageKey pageKey);

    Optional<Section> findByPageKeyAndSectionKey(PageKey pageKey, SectionKey sectionKey);
}
