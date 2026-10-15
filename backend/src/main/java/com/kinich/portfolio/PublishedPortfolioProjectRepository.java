package com.kinich.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PublishedPortfolioProjectRepository extends JpaRepository<PublishedPortfolioProject, UUID> {

    Optional<PublishedPortfolioProject> findBySourceId(UUID sourceId);

    /** Instantánea y galería en una sola consulta: una republicación simultánea no deja una galería vacía. */
    @EntityGraph(attributePaths = "images")
    Optional<PublishedPortfolioProject> findBySlug(String slug);

    List<PublishedPortfolioProject> findAllByOrderByPositionAsc();

    boolean existsBySlugAndSourceIdNot(String slug, UUID sourceId);

    boolean existsBySlug(String slug);

    /**
     * Serializa las publicaciones (proyecto y orden) hasta el fin de la transacción: dos primeras publicaciones a la
     * vez calcularían la misma posición final, y publicar el orden a la vez que un proyecto renumeraría sobre datos
     * viejos. Las lecturas simples (sitio público) no se bloquean.
     */
    @Modifying
    @Query(value = "LOCK TABLE published_portfolio_projects IN EXCLUSIVE MODE", nativeQuery = true)
    void lockForPublication();
}
