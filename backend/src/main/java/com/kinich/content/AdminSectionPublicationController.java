package com.kinich.content;

import com.kinich.auth.AuthenticatedUser;
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
@RequestMapping("/api/admin/pages/home/sections/{sectionKey}/publication")
@RequiredArgsConstructor
class AdminSectionPublicationController {

    private final SectionPublicationService publication;

    @PutMapping
    SectionPublicationResponse publish(@PathVariable String sectionKey,
                                       @Valid @RequestBody SectionPublicationRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        Section section = publication.publish(sectionKey, request.revision(), user.id());
        return new SectionPublicationResponse(section.getSectionKey().slug(), section.getPublishedRevision(),
                section.getPublishedAt());
    }
}
