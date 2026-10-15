package com.kinich.portfolio;

import com.kinich.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Solo HTTP ↔ DTO. Validación de negocio y transacciones viven en los servicios. */
@RestController
@RequestMapping("/api/admin/portfolio/projects")
@RequiredArgsConstructor
class AdminPortfolioProjectController {

    private final PortfolioProjectService projects;
    private final AdminPortfolioReader reader;

    @GetMapping
    AdminProjectListResponse list() {
        return reader.list();
    }

    @GetMapping("/{id}")
    AdminProjectResponse get(@PathVariable UUID id) {
        return reader.project(id);
    }

    @PostMapping
    ResponseEntity<AdminProjectResponse> create(@Valid @RequestBody ProjectRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        UUID id = projects.create(request, user.id()).getId();
        return ResponseEntity.created(URI.create("/api/admin/portfolio/projects/" + id)).body(reader.project(id));
    }

    @PutMapping("/{id}")
    AdminProjectResponse update(@PathVariable UUID id, @Valid @RequestBody ProjectRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        projects.update(id, request, user.id());
        return reader.project(id);
    }

    @PatchMapping("/{id}/visibility")
    AdminProjectResponse changeVisibility(@PathVariable UUID id, @Valid @RequestBody ProjectVisibilityRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        projects.changeVisibility(id, request.hidden(), request.version(), user.id());
        return reader.project(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version, @AuthenticationPrincipal AuthenticatedUser user) {
        projects.delete(id, version, user.id());
    }
}
