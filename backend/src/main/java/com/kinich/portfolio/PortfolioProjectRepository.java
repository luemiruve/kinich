package com.kinich.portfolio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioProjectRepository extends JpaRepository<PortfolioProject, UUID> {

    List<PortfolioProject> findAllByOrderByPositionAsc();

    long countBy();

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
