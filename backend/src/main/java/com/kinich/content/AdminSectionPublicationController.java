package com.kinich.content;

import com.kinich.auth.AuthenticatedUser;
import com.kinich.shared.error.ApiProblemException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** La publicación de una sección es un recurso: PUT con la revision previsualizada. */
@RestController
@RequestMapping("/api/admin/pages/{page}/sections/{sectionKey}/publication")
@RequiredArgsConstructor
class AdminSectionPublicationController {

    private final SectionPublicationService publication;

    @PutMapping
    SectionPublicationResponse publish(@PathVariable String page, @PathVariable String sectionKey,
                                       @Valid @RequestBody SectionPublicationRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        PageKey pageKey = PageKey.fromSlug(page).orElseThrow(ApiProblemException::notFound);
        Section section = publication.publish(pageKey, sectionKey, request.revision(), user.id());
        return new SectionPublicationResponse(section.getSectionKey().slug(), section.getPublishedRevision(),
                section.getPublishedAt());
    }
}
