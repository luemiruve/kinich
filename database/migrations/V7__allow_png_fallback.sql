-- Las imágenes con transparencia real (p. ej. logotipos) usan PNG
-- como respaldo en lugar de JPEG, para no aplanarlas sobre blanco. AVIF sigue siendo la representación prioritaria;
-- las imágenes opacas mantienen JPEG. Nunca WebP.
ALTER TABLE media_files DROP CONSTRAINT media_files_kind_check;
ALTER TABLE media_files ADD CONSTRAINT media_files_kind_check CHECK (
    (role = 'ORIGINAL' AND content_type IN ('image/jpeg','image/png','image/webp','image/avif'))
    OR (role = 'WEB' AND content_type IN ('image/avif','image/jpeg','image/png')
        AND (width IN (480, 768, 1280, 1920, 2560) OR width < 480)));
