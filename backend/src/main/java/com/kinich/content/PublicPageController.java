package com.kinich.content;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/pages/home")
@RequiredArgsConstructor
class PublicPageController {

    private final PublicPageService pages;

    @GetMapping
    PublicPageResponse home() {
        return pages.load();
    }
}
