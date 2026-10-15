package com.kinich.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PortfolioSchemaIntegrationTest {

    private static final String SHA = "b".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private PortfolioProjectRepository projects;

    @Autowired
    private PublishedPortfolioProjectRepository published;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    private UUID insertAsset() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO media_assets (id, media_type, status, content_type, size_bytes, width, height, sha256, "
                + "created_at, updated_at) VALUES (?, 'IMAGE', 'READY', 'image/png', 10, 100, 100, ?, now(), now())", id, SHA);
        return id;
    }

    private UUID insertProject(String slug, String title, UUID asset, double cropX, double cropW, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO portfolio_projects (id, slug, title, cover_media_asset_id, cover_alt, cover_crop_x, "
                + "cover_crop_y, cover_crop_width, cover_crop_height, position, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'alt', ?, 0, ?, 1, ?, now(), now())", id, slug, title, asset, cropX, cropW, position);
        return id;
    }

    private UUID insertProject(String slug, String title, UUID asset) {
        return insertProject(slug, title, asset, 0, 1, 1);
    }

    private UUID insertImage(UUID project, UUID asset, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO portfolio_project_images (id, project_id, media_asset_id, alt, position) "
                + "VALUES (?, ?, ?, 'alt', ?)", id, project, asset, position);
        return id;
    }

    private UUID insertPublished(UUID source, String slug, UUID asset, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO published_portfolio_projects (id, source_id, slug, title, cover_media_asset_id, cover_alt, "
                + "cover_crop_x, cover_crop_y, cover_crop_width, cover_crop_height, position, published_at) "
                + "VALUES (?, ?, ?, 'T', ?, 'alt', 0, 0, 1, 1, ?, now())", id, source, slug, asset, position);
        return id;
    }

    private void insertPublishedImage(UUID publishedProject, UUID asset, int position) {
        jdbc.update("INSERT INTO published_portfolio_project_images (id, published_project_id, media_asset_id, alt, position) "
                + "VALUES (?, ?, ?, 'alt', ?)", UUID.randomUUID(), publishedProject, asset, position);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void blankTitleViolatesCheck() {
        UUID asset = insertAsset();
        assertThatThrownBy(() -> insertProject("boda", "   ", asset)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void slugMustMatchFormat() {
        UUID asset = insertAsset();
        assertThatThrownBy(() -> insertProject("Boda X", "T", asset)).isInstanceOf(DataIntegrityViolationException.class);
        insertProject("boda-x", "T", asset);
    }

    @Test
    void slugMustBeUnique() {
        UUID asset = insertAsset();
        insertProject("boda", "T", asset);
        assertThatThrownBy(() -> insertProject("boda", "U", asset, 0, 1, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cropOutOfRangeViolatesCheck() {
        UUID asset = insertAsset();
        assertThatThrownBy(() -> insertProject("a", "T", asset, 0.5, 0.6, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProject("b", "T", asset, -0.1, 0.5, 2)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProject("c", "T", asset, 0, 0, 3)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAProjectCascadesToImagesAndSnapshot() {
        UUID asset = insertAsset();
        UUID project = insertProject("boda", "T", asset);
        insertImage(project, asset, 1);
        UUID snapshot = insertPublished(project, "boda", asset, 1);
        insertPublishedImage(snapshot, asset, 1);

        jdbc.update("DELETE FROM portfolio_projects WHERE id = ?", project);

        assertThat(count("portfolio_project_images")).isZero();
        assertThat(count("published_portfolio_projects")).isZero();
        assertThat(count("published_portfolio_project_images")).isZero();
    }

    @Test
    void referencedMediaAssetCannotBeDeleted() {
        UUID asset = insertAsset();
        UUID project = insertProject("boda", "T", asset);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_assets WHERE id = ?", asset))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID other = insertAsset();
        insertImage(project, other, 1);
        jdbc.update("DELETE FROM portfolio_project_images WHERE project_id = ?", project);
        insertImage(project, other, 1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_assets WHERE id = ?", other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishedPositionsAreDeferrablyUnique() {
        UUID asset = insertAsset();
        UUID a = insertProject("a", "T", asset, 0, 1, 1);
        UUID b = insertProject("b", "T", asset, 0, 1, 2);
        insertPublished(a, "a", asset, 1);
        insertPublished(b, "b", asset, 2);

        // Intercambiar posiciones dentro de una transacción es válido: la restricción se comprueba al confirmar.
        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE published_portfolio_projects SET position = 3 WHERE slug = 'a'");
            jdbc.update("UPDATE published_portfolio_projects SET position = 1 WHERE slug = 'b'");
            jdbc.update("UPDATE published_portfolio_projects SET position = 2 WHERE slug = 'a'");
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                jdbc.update("UPDATE published_portfolio_projects SET position = 1 WHERE slug = 'a'")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoriesMapTheSchema() {
        UUID asset = insertAsset();
        UUID project = insertProject("boda", "T", asset);
        insertImage(project, asset, 2);
        insertImage(project, asset, 1);
        insertPublished(project, "boda", asset, 1);

        assertThat(projects.countBy()).isEqualTo(1);
        assertThat(projects.findAllByOrderByPositionAsc()).hasSize(1);
        assertThat(projects.existsBySlugAndIdNot("boda", UUID.randomUUID())).isTrue();
        assertThat(projects.existsBySlugAndIdNot("boda", project)).isFalse();
        assertThat(published.findBySourceId(project)).isPresent();
        assertThat(published.findBySlug("boda")).isPresent();
        assertThat(published.findAllByOrderByPositionAsc()).hasSize(1);
        assertThat(published.existsBySlugAndSourceIdNot("boda", project)).isFalse();
        assertThat(published.existsBySlugAndSourceIdNot("boda", UUID.randomUUID())).isTrue();
        tx.executeWithoutResult(s -> assertThat(projects.findById(project).orElseThrow().getImages())
                .extracting(PortfolioProjectImage::getPosition).containsExactly(1, 2));
    }
}
