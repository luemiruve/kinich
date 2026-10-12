package com.kinich.content;

import com.kinich.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content-categories")
@RequiredArgsConstructor
class AdminContentCategoryController {

    private final ContentCategoryService categories;

    @GetMapping
    List<CategoryResponse> list() {
        return categories.list().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        ContentCategory created = categories.create(request.label(), user.id());
        return ResponseEntity.created(URI.create("/api/admin/content-categories/" + created.getId()))
                .body(CategoryResponse.from(created));
    }
}
