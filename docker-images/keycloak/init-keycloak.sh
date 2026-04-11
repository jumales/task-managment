#!/bin/bash
# Starts Keycloak in dev mode, then disables the SSL requirement on all realms
# so the admin console and login work over plain HTTP from remote addresses.

set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh

# Start Keycloak in the background
/opt/keycloak/bin/kc.sh start-dev --import-realm &
KC_PID=$!

# Wait until Keycloak's HTTP port is accepting connections (no curl in the image)
echo "[init-keycloak] Waiting for Keycloak to become ready ..."
until bash -c 'echo > /dev/tcp/localhost/8080' 2>/dev/null; do
  sleep 2
done
# Give the HTTP stack a moment to finish initialising after the port opens
sleep 3
echo "[init-keycloak] Keycloak is up."

# Authenticate with the admin account
$KCADM config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

# Disable SSL requirement so HTTP works from any IP (dev only)
$KCADM update realms/master -s sslRequired=NONE
echo "[init-keycloak] master realm: sslRequired=NONE"

$KCADM update realms/demo -s sslRequired=NONE 2>/dev/null && \
  echo "[init-keycloak] demo realm: sslRequired=NONE" || \
  echo "[init-keycloak] demo realm not found yet — will be set on import"

# Hand off to the Keycloak process
wait $KC_PID
