# Runbook: users and access

Users, passwords, MFA and groups live in Keycloak (realm `tracker`). The app creates its own user
record the first time someone signs in, and never deletes it (D10).

Keycloak admin console: <https://auth.test:8443/admin/master/console/#/tracker> — sign in with the
bootstrap admin from `.env` (`KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`).

## Add a user

1. Keycloak console → **Users** → **Add user**: username, email, first and last name → Create.
2. **Credentials** tab → **Set password**, leave *Temporary* on so they choose their own.
3. Give them the URL. At first sign-in they set a password and **enrol an authenticator app
   (TOTP)** — MFA is required for everyone (ADR-0023).

## Make someone an administrator

Keycloak → the user → **Groups** → **Join group** → `tracker-admins`. Takes effect at their next
sign-in. Removing them from the group removes admin rights at the next sign-in; admin access tokens
stop working immediately (checked on every use).

## Remove someone's access

| Goal | Do this | Effect |
|---|---|---|
| Stop access everywhere, at once | Keycloak: user → **Disable**, then **Sessions** → **Sign out** | Back-channel logout ends app sessions immediately; sign-in refused |
| Disable in Keycloak only | Keycloak: user → **Disable** | App session ends at the next token refresh (≤ 5 minutes, ADR-0025) |
| Stop access to this app only | `POST /api/v1/admin/users/{id}/deactivate` (admin) | Sessions end now, access tokens stop, sign-in refused; history keeps their name |
| Restore | `POST /api/v1/admin/users/{id}/reactivate` and/or enable in Keycloak | |

Find user IDs with `GET /api/v1/admin/users`.

## Locked out after failed sign-ins

Brute-force protection locks an account for a growing interval after 5 failures (up to 15
minutes). Keycloak → user → **Details** shows the lock; an admin can unlock it there.

## Lost authenticator

Keycloak → user → **Credentials** → delete the OTP credential. They enrol a new one at next sign-in.

## Access tokens (scripts and integrations)

Users create them in the app: account menu → **Access tokens**. The value is shown once.

```bash
curl -H "Authorization: Bearer trk_pat_…" https://tracker.test:8443/api/v1/me
```

Scopes: `read`, `write` (includes read), `admin` (admins only). Tokens expire (max 1 year) and stop
working when revoked or when their owner is deactivated (ADR-0024).

## Audit log

`GET /api/v1/admin/audit-events?limit=50` (admin; session or a token with the `admin` scope).
Newest first; page with `before=<id>` from the previous response's `nextBefore`; filter with
`action=` (e.g. `auth.login`, `token.create`, `user.deactivate`, `auth.logout.backchannel`,
`auth.session.end`). The table is append-only: the database rejects updates and deletes.

Keycloak keeps its own login and admin events for 30 days: console → **Events**.

## Realm configuration

The realm is code: `infra/keycloak/realm/tracker.json`, applied by the `keycloak-config` service on
every `docker compose up`. Change settings there, not in the console — console changes to managed
settings are overwritten on the next start.
