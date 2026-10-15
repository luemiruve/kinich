package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {page} desconocida → 404. La ruta de Inicio sigue siendo /api/admin/pages/home. */
@RestController
@RequestMapping("/api/admin/pages/{page}")
@RequiredArgsConstructor
class AdminPageController {

    private final AdminPageService pages;

    @GetMapping
    AdminPageResponse page(@PathVariable String page) {
        return pages.load(PageKey.fromSlug(page).orElseThrow(ApiProblemException::notFound));
    }
}
