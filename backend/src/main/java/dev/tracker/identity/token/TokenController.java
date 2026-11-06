package dev.tracker.identity.token;

import dev.tracker.identity.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage your own personal access tokens. Browser sessions only: a token can't mint or revoke
 * tokens (enforced in the security configuration).
 */
@RestController
@RequestMapping("/api/v1/me/tokens")
@Tag(name = "Me")
class TokenController {

    private final TokenService tokens;

    TokenController(TokenService tokens) {
        this.tokens = tokens;
    }

    record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty Set<String> scopes,
            @Nullable @Min(1) @Max(365) Integer expiresInDays) {}

    @GetMapping
    @Operation(summary = "List your access tokens (never the token values)")
    List<TokenService.View> list(CurrentUser user) {
        return tokens.list(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an access token; the value is returned only in this response")
    TokenService.Created create(CurrentUser user, @Valid @RequestBody CreateRequest request) {
        return tokens.create(user, request.name(), request.scopes(), request.expiresInDays());
    }

    @DeleteMapping("/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an access token")
    void revoke(CurrentUser user, @PathVariable UUID tokenId) {
        tokens.revoke(user, tokenId);
    }
}
