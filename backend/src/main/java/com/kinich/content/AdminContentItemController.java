package com.kinich.content;

import com.kinich.auth.AuthenticatedUser;
import com.kinich.content.ContentItemService.ItemInput;
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
@RequestMapping("/api/admin/pages/home/sections/{sectionKey}/items")
@RequiredArgsConstructor
class AdminContentItemController {

    private final ContentItemService items;
    private final AdminPageService pages;

    @PostMapping
    ResponseEntity<ItemResponse> create(@PathVariable String sectionKey, @Valid @RequestBody ItemCreateRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        ContentItem created = items.create(sectionKey, input(request.mediaId(), request.imageAlt(), request.categoryId(),
                request.eyebrow(), request.title(), request.description(), request.crop(), request.mediaPosition()),
                user.id());
        // sectionKey ya fue validado por el servicio (lista blanca), así que es seguro reflejarlo en Location.
        URI location = URI.create("/api/admin/pages/home/sections/" + sectionKey + "/items/" + created.getId());
        return ResponseEntity.created(location).body(pages.describe(created));
    }

    @PutMapping("/{id}")
    ItemResponse update(@PathVariable String sectionKey, @PathVariable UUID id,
                        @Valid @RequestBody ItemUpdateRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return pages.describe(items.update(sectionKey, id, input(request.mediaId(), request.imageAlt(),
                request.categoryId(), request.eyebrow(), request.title(), request.description(), request.crop(),
                request.mediaPosition()), request.version(), user.id()));
    }

    @PatchMapping("/{id}/visibility")
    ItemResponse changeVisibility(@PathVariable String sectionKey, @PathVariable UUID id,
                                  @Valid @RequestBody ItemVisibilityRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return pages.describe(items.changeVisibility(sectionKey, id, request.hidden(), request.version(), user.id()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String sectionKey, @PathVariable UUID id, @RequestParam long version,
                @AuthenticationPrincipal AuthenticatedUser user) {
        items.delete(sectionKey, id, version, user.id());
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorder(@PathVariable String sectionKey, @Valid @RequestBody ItemOrderRequest request,
                 @AuthenticationPrincipal AuthenticatedUser user) {
        items.reorder(sectionKey, request.revision(), request.ids(), user.id());
    }

    private static ItemInput input(UUID mediaId, String imageAlt, UUID categoryId, String eyebrow, String title,
                                   String description, CropInput crop, FocalPointInput mediaPosition) {
        return new ItemInput(mediaId, imageAlt, categoryId, eyebrow, title, description,
                crop == null ? null : crop.toCrop(), mediaPosition == null ? null : mediaPosition.toFocalPoint());
    }
}
