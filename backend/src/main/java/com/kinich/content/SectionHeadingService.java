package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.text.PlainText;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edita el BORRADOR de la cabecera de una sección; el público la ve al publicar la sección. */
@Slf4j(topic = "kinich.content")
@Service
@RequiredArgsConstructor
public class SectionHeadingService {

    private static final String NOT_ALLOWED = "no aplica en esta sección";
    private static final String TOO_LONG = "supera la longitud máxima";

    private final EditableSectionLookup sections;

    @Transactional
    public Section update(String slug, String eyebrow, String title, String body, long revision, UUID actorId) {
        Section section = sections.require(slug);
        if (section.getDraftRevision() != revision) {
            throw ApiProblemException.versionConflict();
        }
        section.editHeading(normalize(SectionHeadingPolicy.fields(section.getSectionKey()), eyebrow, title, body));
        log.info("Cabecera de sección editada (section={}, userId={})", slug, actorId);
        return section;
    }

    private static SectionHeading normalize(Set<HeadingField> allowed, String eyebrow, String title, String body) {
        Map<String, String> errors = new LinkedHashMap<>();
        SectionHeading heading = new SectionHeading(
                field(allowed, HeadingField.EYEBROW, PlainText.cleanLine("eyebrow", eyebrow, errors), 120, errors),
                field(allowed, HeadingField.TITLE, PlainText.cleanLine("title", title, errors), 200, errors),
                field(allowed, HeadingField.BODY, PlainText.clean("body", body, errors), 600, errors));
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        return heading;
    }

    private static String field(Set<HeadingField> allowed, HeadingField field, String value, int max,
                                Map<String, String> errors) {
        String name = field.name().toLowerCase();
        if (value == null) {
            return null;
        }
        if (!allowed.contains(field)) {
            errors.put(name, NOT_ALLOWED);
            return null;
        }
        if (value.length() > max) {
            errors.put(name, TOO_LONG);
            return null;
        }
        return value;
    }
}
