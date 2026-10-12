package com.kinich.settings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, UUID> {

    /** La única fila de la instalación (UNIQUE sobre singleton). */
    Optional<BusinessSettings> findFirstBy();
}
