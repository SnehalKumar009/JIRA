package dev.tracker.identity.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Whether a user may act, checked on every authenticated request. Cached briefly; changes made
 * through this app invalidate the entry immediately.
 */
@Component
public class ActiveUsers {

    private final UserRepository users;
    private final Cache<UUID, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(10_000)
            .build();

    ActiveUsers(UserRepository users) {
        this.users = users;
    }

    public boolean isActive(UUID userId) {
        return cache.get(userId, users::isActive);
    }

    void invalidate(UUID userId) {
        cache.invalidate(userId);
    }
}
