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

/** Solo HTTP ↔ DTO. Las reglas por sección y la concurrencia viven en SectionHeadingService. */
@RestController
@RequestMapping("/api/admin/pages/home/sections/{sectionKey}/heading")
@RequiredArgsConstructor
class AdminSectionHeadingController {

    private final SectionHeadingService headings;

    @PutMapping
    SectionHeadingResponse update(@PathVariable String sectionKey, @Valid @RequestBody SectionHeadingRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return SectionHeadingResponse.from(headings.update(sectionKey, request.eyebrow(), request.title(),
                request.body(), request.revision(), user.id()));
    }
}
