package dev.tracker.identity.user;

import dev.tracker.identity.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin")
@Validated
class UserAdminController {

    private final UserRepository users;
    private final UserAdminService admin;

    UserAdminController(UserRepository users, UserAdminService admin) {
        this.users = users;
        this.admin = admin;
    }

    @GetMapping
    @Operation(summary = "List users")
    List<UserRepository.UserSummary> list(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return users.list(limit);
    }

    @PostMapping("/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a user: refuse sign-in, end sessions, stop their tokens")
    void deactivate(CurrentUser currentUser, @PathVariable UUID userId) {
        admin.deactivate(currentUser, userId);
    }

    @PostMapping("/{userId}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reactivate a user")
    void reactivate(CurrentUser currentUser, @PathVariable UUID userId) {
        admin.reactivate(currentUser, userId);
    }
}
