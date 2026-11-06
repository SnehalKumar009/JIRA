package dev.tracker.support;

import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cookie jar that behaves like a browser on loopback: browsers treat http://127.0.0.1 as a secure
 * context and send Secure cookies there, which java.net.CookieManager refuses to do. Cookies are
 * keyed by host and path; ports are ignored, as browsers do.
 */
final class LoopbackCookieJar extends CookieHandler {

    private record Key(String host, String path, String name) {}

    private final Map<Key, HttpCookie> cookies = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        String header = cookies.entrySet().stream()
                .filter(e -> e.getKey().host().equals(uri.getHost()) && path.startsWith(e.getKey().path()))
                .filter(e -> !e.getValue().hasExpired())
                .map(e -> e.getKey().name() + "=" + e.getValue().getValue())
                .collect(Collectors.joining("; "));
        return header.isEmpty() ? Map.of() : Map.of("Cookie", List.of(header));
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        responseHeaders.forEach((name, values) -> {
            if (!"set-cookie".equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                for (HttpCookie cookie : HttpCookie.parse(value)) {
                    String path = cookie.getPath() != null ? cookie.getPath() : "/";
                    Key key = new Key(uri.getHost(), path, cookie.getName());
                    if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                        cookies.remove(key);
                    } else {
                        cookies.put(key, cookie);
                    }
                }
            }
        });
    }

    Optional<String> value(String name) {
        List<String> values = new ArrayList<>();
        cookies.forEach((key, cookie) -> {
            if (key.name().equals(name)) {
                values.add(cookie.getValue());
            }
        });
        return values.stream().findFirst();
    }
}
