package com.kinich.content;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pages/home")
@RequiredArgsConstructor
class AdminPageController {

    private final AdminPageService pages;

    @GetMapping
    AdminPageResponse home() {
        return pages.load();
    }
}
