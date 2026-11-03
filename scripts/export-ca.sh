#!/usr/bin/env bash
# Copies Caddy's local root CA to certs/tracker-local-ca.crt, for trusting it on the desktop
# (docs/runbooks/desktop-setup.md). The CA lives in the caddy-data volume and survives restarts.
source "$(dirname "$0")/lib.sh"
export_ca
openssl x509 -in "$CA_CERT" -noout -subject -enddate
log "Root CA written to $CA_CERT"
