package com.kinich.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedPortfolioProjectRepository extends JpaRepository<PublishedPortfolioProject, UUID> {

    Optional<PublishedPortfolioProject> findBySourceId(UUID sourceId);

    Optional<PublishedPortfolioProject> findBySlug(String slug);

    List<PublishedPortfolioProject> findAllByOrderByPositionAsc();

    boolean existsBySlugAndSourceIdNot(String slug, UUID sourceId);
}
