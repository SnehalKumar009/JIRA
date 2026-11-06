package dev.tracker.identity.user;

import static dev.tracker.jooq.Tables.APP_GROUP;
import static dev.tracker.jooq.Tables.APP_USER;
import static dev.tracker.jooq.Tables.GROUP_MEMBERSHIP;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    record Upserted(UUID id, boolean active, boolean created) {}

    /** Creates the user on first login or refreshes the profile; keyed on the IdP subject. */
    Upserted upsert(LoginIdentity identity) {
        Record3<UUID, Boolean, Boolean> row = dsl.insertInto(APP_USER)
                .set(APP_USER.IDP_SUBJECT, identity.subject())
                .set(APP_USER.USERNAME, identity.username())
                .set(APP_USER.DISPLAY_NAME, identity.displayName())
                .set(APP_USER.EMAIL, identity.email())
                .onConflict(APP_USER.IDP_SUBJECT)
                .doUpdate()
                .set(APP_USER.USERNAME, identity.username())
                .set(APP_USER.DISPLAY_NAME, identity.displayName())
                .set(APP_USER.EMAIL, identity.email())
                .set(APP_USER.UPDATED_AT, DSL.currentOffsetDateTime())
                // xmax is 0 only for a freshly inserted row — PostgreSQL's way to tell insert from update.
                .returningResult(APP_USER.ID, APP_USER.ACTIVE, DSL.field("xmax = 0", Boolean.class))
                .fetchSingle();
        return new Upserted(row.value1(), row.value2(), row.value3());
    }

    void recordLogin(UUID userId) {
        dsl.update(APP_USER)
                .set(APP_USER.LAST_LOGIN_AT, DSL.currentOffsetDateTime())
                .where(APP_USER.ID.eq(userId))
                .execute();
    }

    /** Replaces the user's group memberships with exactly {@code names}, creating unknown groups. */
    void replaceGroups(UUID userId, Set<String> names) {
        if (!names.isEmpty()) {
            var insert = dsl.insertInto(APP_GROUP, APP_GROUP.NAME);
            for (String name : names) {
                insert = insert.values(name);
            }
            insert.onConflictDoNothing().execute();
        }
        Map<String, UUID> ids = names.isEmpty()
                ? Map.of()
                : dsl.select(APP_GROUP.NAME, APP_GROUP.ID)
                        .from(APP_GROUP)
                        .where(APP_GROUP.NAME.in(names))
                        .fetchMap(APP_GROUP.NAME, APP_GROUP.ID);

        dsl.deleteFrom(GROUP_MEMBERSHIP)
                .where(GROUP_MEMBERSHIP.USER_ID.eq(userId))
                .and(ids.isEmpty() ? DSL.trueCondition() : GROUP_MEMBERSHIP.GROUP_ID.notIn(ids.values()))
                .execute();
        if (!ids.isEmpty()) {
            var insert = dsl.insertInto(GROUP_MEMBERSHIP, GROUP_MEMBERSHIP.GROUP_ID, GROUP_MEMBERSHIP.USER_ID);
            for (UUID groupId : ids.values()) {
                insert = insert.values(groupId, userId);
            }
            insert.onConflictDoNothing().execute();
        }
    }

    public List<String> groupNames(UUID userId) {
        return dsl.select(APP_GROUP.NAME)
                .from(GROUP_MEMBERSHIP)
                .join(APP_GROUP).on(APP_GROUP.ID.eq(GROUP_MEMBERSHIP.GROUP_ID))
                .where(GROUP_MEMBERSHIP.USER_ID.eq(userId))
                .orderBy(APP_GROUP.NAME)
                .fetch(APP_GROUP.NAME);
    }

    public boolean isMemberOf(UUID userId, String groupName) {
        return dsl.fetchExists(dsl.selectOne()
                .from(GROUP_MEMBERSHIP)
                .join(APP_GROUP).on(APP_GROUP.ID.eq(GROUP_MEMBERSHIP.GROUP_ID))
                .where(GROUP_MEMBERSHIP.USER_ID.eq(userId))
                .and(APP_GROUP.NAME.eq(groupName)));
    }

    public boolean isActive(UUID userId) {
        return dsl.fetchExists(APP_USER, APP_USER.ID.eq(userId).and(APP_USER.ACTIVE));
    }

    record UserSummary(
            UUID id,
            String username,
            String displayName,
            @Nullable String email,
            boolean active,
            OffsetDateTime createdAt,
            @Nullable OffsetDateTime lastLoginAt) {}

    List<UserSummary> list(int limit) {
        return dsl.selectFrom(APP_USER)
                .orderBy(DSL.lower(APP_USER.USERNAME), APP_USER.ID)
                .limit(limit)
                .fetch(r -> new UserSummary(r.getId(), r.getUsername(), r.getDisplayName(), r.getEmail(),
                        r.getActive(), r.getCreatedAt(), r.getLastLoginAt()));
    }

    public Optional<UUID> idOfSubject(String subject) {
        return dsl.select(APP_USER.ID).from(APP_USER).where(APP_USER.IDP_SUBJECT.eq(subject)).fetchOptional(APP_USER.ID);
    }

    Optional<String> subjectOf(UUID userId) {
        return dsl.select(APP_USER.IDP_SUBJECT).from(APP_USER).where(APP_USER.ID.eq(userId)).fetchOptional(APP_USER.IDP_SUBJECT);
    }

    /** @return true if the flag changed */
    boolean setActive(UUID userId, boolean active) {
        return dsl.update(APP_USER)
                .set(APP_USER.ACTIVE, active)
                .set(APP_USER.DEACTIVATED_AT, active ? DSL.inline(null, APP_USER.DEACTIVATED_AT) : DSL.currentOffsetDateTime())
                .set(APP_USER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(APP_USER.ID.eq(userId))
                .and(APP_USER.ACTIVE.ne(active))
                .execute() == 1;
    }
}
