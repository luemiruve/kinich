package com.kinich.settings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SocialLinkRepository extends JpaRepository<SocialLink, UUID> {

    List<SocialLink> findAllByOrderByPosition();

    List<SocialLink> findByVisibleTrueOrderByPosition();
}
