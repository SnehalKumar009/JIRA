package dev.tracker.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal browser: keeps cookies, follows redirects, signs in through Keycloak's login form,
 * and sends the CSRF header like the SPA does.
 */
public final class Browser {

    private static final Pattern LOGIN_FORM = Pattern.compile("id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");

    private final LoopbackCookieJar cookies = new LoopbackCookieJar();
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String appBaseUrl;

    public Browser(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public Browser signIn(String username, String password) {
        signInResponse(username, password);
        return this;
    }

    /** Ends with the final response after redirects, e.g. the app page after sign-in. */
    public HttpResponse<String> signInResponse(String username, String password) {
        HttpResponse<String> loginPage = send(HttpRequest.newBuilder(URI.create(appBaseUrl + "/oauth2/authorization/keycloak")).GET());
        Matcher form = LOGIN_FORM.matcher(loginPage.body());
        if (!form.find()) {
            throw new IllegalStateException("Keycloak login form not found at " + loginPage.uri() + " (HTTP " + loginPage.statusCode() + "):\n" + loginPage.body().substring(0, Math.min(1500, loginPage.body().length())));
        }
        String action = form.group(1).replace("&amp;", "&");
        return send(HttpRequest.newBuilder(URI.create(action))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "username=" + encode(username) + "&password=" + encode(password) + "&credentialId=")));
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(URI.create(appBaseUrl + path)).GET());
    }

    public HttpResponse<String> postJson(String path, String json) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(appBaseUrl + path)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> postJsonWithoutCsrf(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(appBaseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> delete(String path) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(appBaseUrl + path))).DELETE());
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder request) {
        return cookies.value("XSRF-TOKEN")
                .map(token -> request.header("X-XSRF-TOKEN", token))
                .orElse(request);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
