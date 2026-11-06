package dev.tracker.support;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import dev.tracker.TestcontainersConfiguration;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.Testcontainers;

/**
 * Base for tests that sign in through a real Keycloak (ADR-0014), started once per JVM with the
 * same realm file the stack uses. The app runs on a fixed free port so Keycloak can reach it for
 * back-channel logout through host.testcontainers.internal.
 *
 * <p>Everything uses 127.0.0.1, never "localhost": Java's cookie store files cookies for dotless
 * hosts under "localhost.local" and never sends them back, and Keycloak (dev mode) derives the token
 * issuer from the request host, so the browser, the app and the admin client must agree on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class KeycloakIntegrationTest {

    protected static final String REALM = "tracker";
    protected static final String CLIENT_SECRET = "integration-test-secret";
    protected static final int APP_PORT = freePort();

    protected static final KeycloakContainer KEYCLOAK;
    private static final String KEYCLOAK_URL;
    private static final Keycloak ADMIN;

    static {
        Testcontainers.exposeHostPorts(APP_PORT);
        KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
                .withRealmImportFile(realmFileForTests());
        KEYCLOAK.start();
        KEYCLOAK_URL = "http://127.0.0.1:" + KEYCLOAK.getHttpPort();
        ADMIN = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK_URL)
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();
    }

    @DynamicPropertySource
    static void appProperties(DynamicPropertyRegistry registry) {
        String realmUrl = KEYCLOAK_URL + "/realms/" + REALM;
        registry.add("server.port", () -> APP_PORT);
        registry.add("tracker.oidc.issuer", () -> realmUrl);
        registry.add("tracker.oidc.backchannel-url", () -> realmUrl);
        registry.add("tracker.oidc.client-secret", () -> CLIENT_SECRET);
    }

    protected static String appUrl(String path) {
        return "http://127.0.0.1:" + APP_PORT + path;
    }

    protected static RealmResource realm() {
        return ADMIN.realm(REALM);
    }

    /**
     * The realm file carries $(env:...) placeholders that keycloak-config-cli fills in on the stack.
     * Keycloak's own import validates URLs, so fill them in here and import the result.
     */
    private static String realmFileForTests() {
        try {
            Path source = Path.of(KeycloakIntegrationTest.class.getResource("/keycloak/tracker.json").toURI());
            String realm = Files.readString(source)
                    .replace("$(env:TRACKER_OIDC_CLIENT_SECRET)", CLIENT_SECRET)
                    .replace("$(env:TRACKER_PUBLIC_URL)", appUrl(""))
                    .replace("$(env:TRACKER_BACKCHANNEL_LOGOUT_URL)",
                            "http://host.testcontainers.internal:" + APP_PORT + "/logout/connect/back-channel/keycloak");
            if (realm.contains("$(env:")) {
                throw new IllegalStateException("Realm file has a placeholder the tests don't fill in");
            }
            Files.writeString(source.resolveSibling("tracker-test.json"), realm);
            return "keycloak/tracker-test.json";
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A Keycloak user with a password and no required actions (so no MFA enrolment in tests). */
    protected static TestUser createUser(boolean admin) {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String password = "Pw-" + UUID.randomUUID();
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setFirstName("Test");
        user.setLastName(username);
        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setRequiredActions(List.of());
        String id;
        try (Response response = realm().users().create(user)) {
            id = CreatedResponseUtil.getCreatedId(response);
        }
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        realm().users().get(id).resetPassword(credential);
        UserRepresentation created = realm().users().get(id).toRepresentation();
        created.setRequiredActions(List.of());
        realm().users().get(id).update(created);
        if (admin) {
            String groupId = realm().groups().groups("tracker-admins", 0, 1).getFirst().getId();
            realm().users().get(id).joinGroup(groupId);
        }
        return new TestUser(id, username, password);
    }

    protected record TestUser(String keycloakId, String username, String password) {}

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
