package dev.tracker.identity.token;

import static dev.tracker.jooq.Tables.APP_USER;
import static dev.tracker.jooq.Tables.PERSONAL_ACCESS_TOKEN;

import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.identity.CurrentUser;
import dev.tracker.identity.config.OidcProperties;
import dev.tracker.identity.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Personal access tokens: {@code trk_pat_} + 256 random bits, shown once, stored as SHA-256.
 * Scopes: {@code read} (safe methods), {@code write} (implies read), {@code admin} (admin API, only
 * for admins). A token works only while unrevoked, unexpired and its owner is active.
 */
@Service
public class TokenService {

    static final String PREFIX = "trk_pat_";
    static final Set<String> SCOPES = Set.of("read", "write", "admin");
    static final int DEFAULT_EXPIRY_DAYS = 90;
    static final int MAX_ACTIVE_TOKENS = 50;

    private static final int TOKEN_BYTES = 32;
    private static final int ENCODED_LENGTH = 43; // base64url of 32 bytes, unpadded

    private final DSLContext dsl;
    private final UserRepository users;
    private final OidcProperties oidc;
    private final AuditLog audit;
    private final SecureRandom random = new SecureRandom();

    TokenService(DSLContext dsl, UserRepository users, OidcProperties oidc, AuditLog audit) {
        this.dsl = dsl;
        this.users = users;
        this.oidc = oidc;
        this.audit = audit;
    }

    record Created(UUID id, String name, String token, Set<String> scopes, OffsetDateTime expiresAt) {}

    record View(
            UUID id,
            String name,
            String displayPrefix,
            Set<String> scopes,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            @Nullable OffsetDateTime lastUsedAt,
            @Nullable OffsetDateTime revokedAt,
            String status) {}

    @Transactional
    Created create(CurrentUser owner, String name, Set<String> requestedScopes, @Nullable Integer expiresInDays) {
        Set<String> scopes = new TreeSet<>(requestedScopes);
        if (scopes.isEmpty() || !SCOPES.containsAll(scopes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scopes must be some of " + new TreeSet<>(SCOPES));
        }
        if (scopes.contains("admin") && !owner.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can create admin tokens");
        }
        int active = dsl.fetchCount(PERSONAL_ACCESS_TOKEN, PERSONAL_ACCESS_TOKEN.USER_ID.eq(owner.userId())
                .and(PERSONAL_ACCESS_TOKEN.REVOKED_AT.isNull())
                .and(PERSONAL_ACCESS_TOKEN.EXPIRES_AT.gt(DSL.currentOffsetDateTime())));
        if (active >= MAX_ACTIVE_TOKENS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have " + MAX_ACTIVE_TOKENS + " active tokens; revoke one first");
        }

        byte[] secret = new byte[TOKEN_BYTES];
        random.nextBytes(secret);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusDays(expiresInDays != null ? expiresInDays : DEFAULT_EXPIRY_DAYS);

        UUID id = dsl.insertInto(PERSONAL_ACCESS_TOKEN)
                .set(PERSONAL_ACCESS_TOKEN.USER_ID, owner.userId())
                .set(PERSONAL_ACCESS_TOKEN.NAME, name.strip())
                .set(PERSONAL_ACCESS_TOKEN.TOKEN_HASH, sha256(token))
                .set(PERSONAL_ACCESS_TOKEN.DISPLAY_PREFIX, token.substring(0, PREFIX.length() + 4))
                .set(PERSONAL_ACCESS_TOKEN.SCOPES, scopes.toArray(String[]::new))
                .set(PERSONAL_ACCESS_TOKEN.EXPIRES_AT, expiresAt)
                .returningResult(PERSONAL_ACCESS_TOKEN.ID)
                .fetchSingle(PERSONAL_ACCESS_TOKEN.ID);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name.strip());
        details.put("scopes", List.copyOf(scopes));
        details.put("expiresAt", expiresAt.toString());
        audit.record(AuditRecord.success(owner.auditActor(), "token.create").target("token", id).details(details));
        return new Created(id, name.strip(), token, scopes, expiresAt);
    }

    List<View> list(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.selectFrom(PERSONAL_ACCESS_TOKEN)
                .where(PERSONAL_ACCESS_TOKEN.USER_ID.eq(userId))
                .orderBy(PERSONAL_ACCESS_TOKEN.CREATED_AT.desc())
                .fetch(r -> new View(r.getId(), r.getName(), r.getDisplayPrefix(), new TreeSet<>(List.of(r.getScopes())),
                        r.getCreatedAt(), r.getExpiresAt(), r.getLastUsedAt(), r.getRevokedAt(),
                        r.getRevokedAt() != null ? "revoked" : r.getExpiresAt().isBefore(now) ? "expired" : "active"));
    }

    @Transactional
    void revoke(CurrentUser owner, UUID tokenId) {
        int updated = dsl.update(PERSONAL_ACCESS_TOKEN)
                .set(PERSONAL_ACCESS_TOKEN.REVOKED_AT, DSL.currentOffsetDateTime())
                .where(PERSONAL_ACCESS_TOKEN.ID.eq(tokenId))
                .and(PERSONAL_ACCESS_TOKEN.USER_ID.eq(owner.userId()))
                .and(PERSONAL_ACCESS_TOKEN.REVOKED_AT.isNull())
                .execute();
        if (updated == 0) {
            boolean owned = dsl.fetchExists(PERSONAL_ACCESS_TOKEN, PERSONAL_ACCESS_TOKEN.ID.eq(tokenId)
                    .and(PERSONAL_ACCESS_TOKEN.USER_ID.eq(owner.userId())));
            if (!owned) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such token");
            }
            return; // already revoked: idempotent
        }
        audit.record(AuditRecord.success(owner.auditActor(), "token.revoke").target("token", tokenId));
    }

    /** Resolves a presented token to its principal, or empty if it is unknown or no longer usable. */
    Optional<TokenPrincipal> authenticate(String presented) {
        if (!presented.startsWith(PREFIX) || presented.length() != PREFIX.length() + ENCODED_LENGTH) {
            return Optional.empty();
        }
        var row = dsl.select(PERSONAL_ACCESS_TOKEN.ID, PERSONAL_ACCESS_TOKEN.USER_ID, PERSONAL_ACCESS_TOKEN.SCOPES,
                        APP_USER.IDP_SUBJECT, APP_USER.USERNAME, APP_USER.DISPLAY_NAME, APP_USER.EMAIL)
                .from(PERSONAL_ACCESS_TOKEN)
                .join(APP_USER).on(APP_USER.ID.eq(PERSONAL_ACCESS_TOKEN.USER_ID))
                .where(PERSONAL_ACCESS_TOKEN.TOKEN_HASH.eq(sha256(presented)))
                .and(PERSONAL_ACCESS_TOKEN.REVOKED_AT.isNull())
                .and(PERSONAL_ACCESS_TOKEN.EXPIRES_AT.gt(DSL.currentOffsetDateTime()))
                .and(APP_USER.ACTIVE)
                .fetchOptional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        var r = row.get();
        UUID tokenId = r.get(PERSONAL_ACCESS_TOKEN.ID);
        UUID userId = r.get(PERSONAL_ACCESS_TOKEN.USER_ID);
        Set<String> scopes = new TreeSet<>(List.of(r.get(PERSONAL_ACCESS_TOKEN.SCOPES)));
        boolean admin = scopes.contains("admin") && users.isMemberOf(userId, oidc.adminGroup());
        touch(tokenId);
        return Optional.of(new TokenPrincipal(tokenId, userId, r.get(APP_USER.IDP_SUBJECT), r.get(APP_USER.USERNAME),
                r.get(APP_USER.DISPLAY_NAME), r.get(APP_USER.EMAIL), scopes, admin, authorities(scopes, admin)));
    }

    private static List<GrantedAuthority> authorities(Set<String> scopes, boolean admin) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (scopes.contains("read") || scopes.contains("write")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_read"));
        }
        if (scopes.contains("write")) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_write"));
        }
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_admin"));
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    // At most one write a minute per token, not one per request.
    private void touch(UUID tokenId) {
        dsl.update(PERSONAL_ACCESS_TOKEN)
                .set(PERSONAL_ACCESS_TOKEN.LAST_USED_AT, DSL.currentOffsetDateTime())
                .where(PERSONAL_ACCESS_TOKEN.ID.eq(tokenId))
                .and(PERSONAL_ACCESS_TOKEN.LAST_USED_AT.isNull()
                        .or(PERSONAL_ACCESS_TOKEN.LAST_USED_AT.lt(OffsetDateTime.now().minusMinutes(1))))
                .execute();
    }

    static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
