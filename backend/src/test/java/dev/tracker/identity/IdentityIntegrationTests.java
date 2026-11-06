package dev.tracker.identity;

import static dev.tracker.jooq.Tables.APP_USER;
import static dev.tracker.jooq.Tables.AUDIT_EVENT;
import static dev.tracker.jooq.Tables.PERSONAL_ACCESS_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tracker.support.Browser;
import dev.tracker.support.KeycloakIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class IdentityIntegrationTests extends KeycloakIntegrationTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    DSLContext dsl;

    @Test
    void signInCreatesTheUserSyncsGroupsAndIsAudited() {
        TestUser user = createUser(true);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());

        JsonNode me = json.readTree(browser.get("/api/v1/me").body());
        assertThat(me.get("username").asString()).isEqualTo(user.username());
        assertThat(me.get("email").asString()).isEqualTo(user.username() + "@example.test");
        assertThat(me.get("admin").asBoolean()).isTrue();
        assertThat(me.get("authMethod").asString()).isEqualTo("SESSION");
        assertThat(me.get("groups").toString()).contains("tracker-admins");

        UUID userId = UUID.fromString(me.get("id").asString());
        var row = dsl.selectFrom(APP_USER).where(APP_USER.ID.eq(userId)).fetchSingle();
        assertThat(row.getIdpSubject()).isEqualTo(user.keycloakId());
        assertThat(row.getLastLoginAt()).isNotNull();
        assertThat(auditActionsFor(userId)).contains("user.create", "auth.login");
    }

    @Test
    void groupMembershipFollowsTheIdentityProviderOnEachSignIn() {
        TestUser user = createUser(true);
        assertThat(json.readTree(new Browser(appUrl("")).signIn(user.username(), user.password())
                .get("/api/v1/me").body()).get("admin").asBoolean()).isTrue();

        String groupId = realm().groups().groups("tracker-admins", 0, 1).getFirst().getId();
        realm().users().get(user.keycloakId()).leaveGroup(groupId);

        Browser again = new Browser(appUrl("")).signIn(user.username(), user.password());
        JsonNode me = json.readTree(again.get("/api/v1/me").body());
        assertThat(me.get("admin").asBoolean()).isFalse();
        assertThat(me.get("groups").toString()).doesNotContain("tracker-admins");
        assertThat(again.get("/api/v1/admin/users").statusCode()).isEqualTo(403);
    }

    @Test
    void apiWithoutCredentialsGetsProblem401AndTheSpaShellStaysPublic() {
        HttpResponse<String> response = new Browser(appUrl("")).get("/api/v1/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"requestId\"");
        assertThat(new Browser(appUrl("")).get("/api/v1/system/info").statusCode()).isEqualTo(200);
    }

    @Test
    void sessionWritesRequireTheCsrfHeader() {
        TestUser user = createUser(false);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());

        HttpResponse<String> response = browser.postJsonWithoutCsrf("/api/v1/me/tokens", tokenRequest("x", "read"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void accessTokenIsShownOnceStoredHashedAndScoped() {
        TestUser user = createUser(false);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());

        HttpResponse<String> created = browser.postJson("/api/v1/me/tokens", tokenRequest("ci", "read"));
        assertThat(created.statusCode()).isEqualTo(201);
        String token = json.readTree(created.body()).get("token").asString();
        assertThat(token).startsWith("trk_pat_").hasSize(8 + 43);

        // Listing never returns the value; the database holds only a hash.
        assertThat(browser.get("/api/v1/me/tokens").body()).doesNotContain(token).contains("trk_pat_");
        assertThat(dsl.fetchExists(PERSONAL_ACCESS_TOKEN,
                PERSONAL_ACCESS_TOKEN.DISPLAY_PREFIX.eq(token.substring(0, 12)))).isTrue();

        assertThat(bearer("GET", "/api/v1/me", token).body()).contains("\"authMethod\":\"TOKEN\"");
        // read scope: no writes, no admin API, and never token management.
        assertThat(bearer("POST", "/api/v1/admin/users/" + UUID.randomUUID() + "/deactivate", token).statusCode())
                .isEqualTo(403);
        assertThat(bearer("GET", "/api/v1/me/tokens", token).statusCode()).isEqualTo(403);
    }

    @Test
    void adminScopeNeedsAnAdminAndUnlocksTheAdminApi() {
        TestUser regular = createUser(false);
        Browser regularBrowser = new Browser(appUrl("")).signIn(regular.username(), regular.password());
        assertThat(regularBrowser.postJson("/api/v1/me/tokens", tokenRequest("x", "admin")).statusCode()).isEqualTo(403);

        TestUser admin = createUser(true);
        Browser adminBrowser = new Browser(appUrl("")).signIn(admin.username(), admin.password());
        String token = json.readTree(adminBrowser.postJson("/api/v1/me/tokens",
                tokenRequest("ops", "read", "write", "admin")).body()).get("token").asString();

        assertThat(bearer("GET", "/api/v1/admin/users", token).statusCode()).isEqualTo(200);
        assertThat(bearer("POST", "/api/v1/admin/users/" + UUID.randomUUID() + "/deactivate", token).statusCode())
                .isEqualTo(404);
    }

    @Test
    void revokedAndExpiredTokensAreRefused() {
        TestUser user = createUser(false);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());
        JsonNode revoked = json.readTree(browser.postJson("/api/v1/me/tokens", tokenRequest("a", "read")).body());
        JsonNode expired = json.readTree(browser.postJson("/api/v1/me/tokens", tokenRequest("b", "read")).body());

        assertThat(browser.delete("/api/v1/me/tokens/" + revoked.get("id").asString()).statusCode()).isEqualTo(204);
        dsl.update(PERSONAL_ACCESS_TOKEN)
                .set(PERSONAL_ACCESS_TOKEN.CREATED_AT, OffsetDateTime.now().minusDays(2))
                .set(PERSONAL_ACCESS_TOKEN.EXPIRES_AT, OffsetDateTime.now().minusDays(1))
                .where(PERSONAL_ACCESS_TOKEN.ID.eq(UUID.fromString(expired.get("id").asString())))
                .execute();

        for (JsonNode token : List.of(revoked, expired)) {
            HttpResponse<String> response = bearer("GET", "/api/v1/me", token.get("token").asString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                    header -> assertThat(header).contains("invalid_token"));
        }
        assertThat(auditActionsForTarget(revoked.get("id").asString())).contains("token.create", "token.revoke");
    }

    @Test
    void deactivationEndsSessionsStopsTokensAndRefusesSignIn() {
        TestUser admin = createUser(true);
        Browser adminBrowser = new Browser(appUrl("")).signIn(admin.username(), admin.password());
        TestUser user = createUser(false);
        Browser userBrowser = new Browser(appUrl("")).signIn(user.username(), user.password());
        String token = json.readTree(userBrowser.postJson("/api/v1/me/tokens", tokenRequest("t", "read")).body())
                .get("token").asString();
        String userId = json.readTree(userBrowser.get("/api/v1/me").body()).get("id").asString();

        assertThat(adminBrowser.postJson("/api/v1/admin/users/" + userId + "/deactivate", "").statusCode())
                .isEqualTo(204);

        assertThat(userBrowser.get("/api/v1/me").statusCode()).isEqualTo(401);
        assertThat(bearer("GET", "/api/v1/me", token).statusCode()).isEqualTo(401);
        HttpResponse<String> signIn = new Browser(appUrl("")).signInResponse(user.username(), user.password());
        assertThat(signIn.uri().getQuery()).isEqualTo("loginError=account_disabled");
        assertThat(auditActionsForTarget(userId)).contains("user.deactivate");

        assertThat(adminBrowser.postJson("/api/v1/admin/users/" + userId + "/reactivate", "").statusCode())
                .isEqualTo(204);
        assertThat(new Browser(appUrl("")).signIn(user.username(), user.password()).get("/api/v1/me").statusCode())
                .isEqualTo(200);
    }

    @Test
    void adminsCannotDeactivateThemselves() {
        TestUser admin = createUser(true);
        Browser browser = new Browser(appUrl("")).signIn(admin.username(), admin.password());
        String id = json.readTree(browser.get("/api/v1/me").body()).get("id").asString();

        assertThat(browser.postJson("/api/v1/admin/users/" + id + "/deactivate", "").statusCode()).isEqualTo(409);
    }

    @Test
    void signOutEndsTheSessionAndHandsTheSpaKeycloaksEndSessionUrl() {
        TestUser user = createUser(false);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());
        String userId = json.readTree(browser.get("/api/v1/me").body()).get("id").asString();

        HttpResponse<String> response = browser.postJson("/logout", "");

        assertThat(response.statusCode()).isEqualTo(200);
        String redirect = json.readTree(response.body()).get("redirectUrl").asString();
        assertThat(redirect).contains("/realms/tracker/protocol/openid-connect/logout", "id_token_hint=",
                "post_logout_redirect_uri=");
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
        assertThat(auditActionsForTarget(userId)).contains("auth.logout");
    }

    @Test
    void keycloakLogoutEndsTheAppSessionThroughBackChannelLogout() throws InterruptedException {
        TestUser user = createUser(false);
        Browser browser = new Browser(appUrl("")).signIn(user.username(), user.password());
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(200);

        realm().users().get(user.keycloakId()).logout();

        int status = 200;
        for (int attempt = 0; attempt < 20 && status == 200; attempt++) {
            Thread.sleep(250);
            status = browser.get("/api/v1/me").statusCode();
        }
        assertThat(status).isEqualTo(401);
    }

    private HttpResponse<String> bearer(String method, String path, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appUrl(path)))
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenRequest(String name, String... scopes) {
        return "{\"name\":\"" + name + "\",\"scopes\":[\"" + String.join("\",\"", scopes) + "\"],\"expiresInDays\":1}";
    }

    private List<String> auditActionsFor(UUID userId) {
        return dsl.select(AUDIT_EVENT.ACTION).from(AUDIT_EVENT)
                .where(AUDIT_EVENT.ACTOR_USER_ID.eq(userId).or(AUDIT_EVENT.TARGET_ID.eq(userId.toString())))
                .fetch(AUDIT_EVENT.ACTION);
    }

    private List<String> auditActionsForTarget(String targetId) {
        return dsl.select(AUDIT_EVENT.ACTION).from(AUDIT_EVENT).where(AUDIT_EVENT.TARGET_ID.eq(targetId))
                .fetch(AUDIT_EVENT.ACTION);
    }
}
