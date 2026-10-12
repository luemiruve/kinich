package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = "kinich.content")
@Service
@RequiredArgsConstructor
public class ContentCategoryService {

    static final int MAX_LABEL = 60;
    private static final String DUPLICATED = "ya existe una categoría con ese nombre";

    private final ContentCategoryRepository categories;

    @Transactional(readOnly = true)
    public List<ContentCategory> list() {
        return categories.findAllByOrderByLabelAsc();
    }

    /** La unicidad sin distinguir mayúsculas la garantiza el índice; una carrera también acaba en 400, nunca en 500. */
    @Transactional
    public ContentCategory create(String label, UUID actorId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String clean = TextNormalizer.clean("label", label, errors);
        if (clean == null && !errors.containsKey("label")) {
            errors.put("label", "es obligatorio");
        } else if (clean != null && clean.length() > MAX_LABEL) {
            errors.put("label", "supera el máximo de " + MAX_LABEL + " caracteres");
        } else if (clean != null && categories.existsByLabelIgnoreCase(clean)) {
            errors.put("label", DUPLICATED);
        }
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        try {
            ContentCategory created = categories.saveAndFlush(new ContentCategory(clean));
            log.info("Categoría creada (id={}, userId={})", created.getId(), actorId);
            return created;
        } catch (DataIntegrityViolationException e) {
            throw ApiProblemException.validation(Map.of("label", DUPLICATED));
        }
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return categories.existsById(id);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> labels(Collection<UUID> ids) {
        return categories.findAllById(ids).stream()
                .collect(Collectors.toMap(ContentCategory::getId, ContentCategory::getLabel));
    }
}
