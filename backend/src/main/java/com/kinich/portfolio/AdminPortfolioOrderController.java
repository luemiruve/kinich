package com.kinich.portfolio;

import com.kinich.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Orden del borrador (D-15) y su publicación, una acción aparte a nivel de colección (R-1). */
@RestController
@RequestMapping("/api/admin/portfolio")
@RequiredArgsConstructor
class AdminPortfolioOrderController {

    private final PortfolioOrderService order;
    private final AdminPortfolioReader reader;

    @PutMapping("/projects/order")
    AdminProjectListResponse reorderDraft(@Valid @RequestBody ProjectOrderRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        order.reorderDraft(request.projects(), user.id());
        return reader.list();
    }

    @PutMapping("/order/publication")
    AdminProjectListResponse publishOrder(@Valid @RequestBody ProjectOrderRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        order.publishOrder(request.projects(), user.id());
        return reader.list();
    }
}
