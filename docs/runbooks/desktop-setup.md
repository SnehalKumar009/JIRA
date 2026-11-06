# Runbook: desktop setup (one time)

Everything here changes your own machine, so you do it yourself; the scripts never touch system
settings.

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Docker Engine + Compose plugin | Compose v2+ | `docker compose version` |
| JDK | 21 (ADR-0017) | `java -version` |
| Node.js | 22 | `node --version` |
| openssl, curl | any | |

## 2. Hostnames

The browser and the containers must use the same URLs (ADR-0012). Add to `/etc/hosts`:

```
127.0.0.1 tracker.test auth.test
```

```bash
echo '127.0.0.1 tracker.test auth.test' | sudo tee -a /etc/hosts
```

## 3. Secrets

```bash
scripts/init-env.sh
```

Creates `.env` (mode 600) with generated secrets. Never commit it, and keep a copy somewhere safe:
the database passwords in it are baked into the data volume on first start.

## 4. First start

```bash
docker compose up -d --wait
```

The first build downloads base images and dependencies and takes a few minutes; later starts take
under a minute.

## 5. Trust the local certificate authority

Caddy creates its own root CA on first start (ADR-0012). Export it:

```bash
scripts/export-ca.sh
```

That writes `certs/tracker-local-ca.crt`. Trust it in:

**System (curl, Java, most CLI tools):**

```bash
sudo cp certs/tracker-local-ca.crt /usr/local/share/ca-certificates/tracker-local-ca.crt
sudo update-ca-certificates
```

**Chrome / Chromium (Linux uses its own NSS store):**

```bash
certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n "Tracker local CA" -i certs/tracker-local-ca.crt
```

(`certutil` is in the `libnss3-tools` package.)

**Firefox:** Settings → Privacy & Security → Certificates → View Certificates → Authorities →
Import → `certs/tracker-local-ca.crt` → tick "Trust this CA to identify websites".

The CA is stored in the `caddy-data` volume and lasts 10 years. It only changes if that volume is
deleted — then repeat this step.

## 6. Create your user

Follow [users-and-access.md](users-and-access.md): add yourself in the Keycloak console and join
`tracker-admins`. At first sign-in you'll set a password and enrol an authenticator app.

## 7. Check

Open <https://tracker.test:8443> → **Sign in**. After signing in you should see the Tracker home
page with your name and groups, and no certificate warning.

## Why port 8443, not 443

On this desktop MicroK8s' ingress controller intercepts ports 80 and 443 before Docker sees them.
The port is configurable with `TRACKER_HTTPS_PORT` in `.env` (ADR-0019); it is used inside the
containers too, so change it before first start or expect the Keycloak issuer URL to change.
