package com.kinich.content;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentCategoryRepository extends JpaRepository<ContentCategory, UUID> {

    List<ContentCategory> findAllByOrderByLabelAsc();

    boolean existsByLabelIgnoreCase(String label);
}
