package dev.tracker.identity.user;

import dev.tracker.identity.AuthMethod;
import dev.tracker.identity.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Me")
class MeController {

    private final UserRepository users;

    MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/v1/me")
    @Operation(summary = "The signed-in user")
    Me me(CurrentUser user) {
        return new Me(user.userId(), user.username(), user.displayName(), user.email(), user.admin(),
                users.groupNames(user.userId()), user.authMethod());
    }

    record Me(
            UUID id,
            String username,
            String displayName,
            @Nullable String email,
            boolean admin,
            List<String> groups,
            AuthMethod authMethod) {}
}
