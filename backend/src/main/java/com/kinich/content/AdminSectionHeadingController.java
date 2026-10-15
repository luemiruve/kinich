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

/** Solo HTTP ↔ DTO. Las reglas por sección y la concurrencia viven en SectionHeadingService. */
@RestController
@RequestMapping("/api/admin/pages/{page}/sections/{sectionKey}/heading")
@RequiredArgsConstructor
class AdminSectionHeadingController {

    private final SectionHeadingService headings;

    @PutMapping
    SectionHeadingResponse update(@PathVariable String page, @PathVariable String sectionKey,
                                  @Valid @RequestBody SectionHeadingRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        PageKey pageKey = PageKey.fromSlug(page).orElseThrow(ApiProblemException::notFound);
        return SectionHeadingResponse.from(headings.update(pageKey, sectionKey, request.eyebrow(), request.title(),
                request.body(), request.revision(), user.id()));
    }
}
