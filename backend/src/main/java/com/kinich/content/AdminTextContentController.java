package com.kinich.content;

import com.kinich.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Solo HTTP ↔ DTO. Autorización por recurso, validación de negocio y transacciones viven en los servicios. */
@RestController
@RequestMapping("/api/admin/pages/home/sections/{sectionKey}/texts")
@RequiredArgsConstructor
class AdminTextContentController {

    private final TextContentService texts;
    private final AdminPageService pages;

    @PostMapping
    ResponseEntity<TextBlockResponse> create(@PathVariable String sectionKey,
                                             @Valid @RequestBody TextBlockCreateRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        TextContent created = texts.create(sectionKey, request.eyebrow(), request.title(), request.body(), user.id());
        // sectionKey ya fue validado por el servicio (lista blanca), así que es seguro reflejarlo en Location.
        URI location = URI.create("/api/admin/pages/home/sections/" + sectionKey + "/texts/" + created.getId());
        return ResponseEntity.created(location).body(pages.describe(created));
    }

    @PutMapping("/{id}")
    TextBlockResponse update(@PathVariable String sectionKey, @PathVariable UUID id,
                             @Valid @RequestBody TextBlockUpdateRequest request,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        return pages.describe(texts.update(sectionKey, id, request.eyebrow(), request.title(), request.body(),
                request.version(), user.id()));
    }

    @PatchMapping("/{id}/visibility")
    TextBlockResponse changeVisibility(@PathVariable String sectionKey, @PathVariable UUID id,
                                       @Valid @RequestBody TextBlockVisibilityRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return pages.describe(texts.changeVisibility(sectionKey, id, request.hidden(), request.version(), user.id()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String sectionKey, @PathVariable UUID id, @RequestParam long version,
                @AuthenticationPrincipal AuthenticatedUser user) {
        texts.delete(sectionKey, id, version, user.id());
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorder(@PathVariable String sectionKey, @Valid @RequestBody TextBlockOrderRequest request,
                 @AuthenticationPrincipal AuthenticatedUser user) {
        texts.reorder(sectionKey, request.revision(), request.ids(), user.id());
    }
}
