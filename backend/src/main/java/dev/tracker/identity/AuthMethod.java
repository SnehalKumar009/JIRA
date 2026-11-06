package dev.tracker.identity;

/** How the current request was authenticated. */
public enum AuthMethod {
    /** Browser session created by OIDC login. */
    SESSION,
    /** Personal access token in the Authorization header. */
    TOKEN
}
