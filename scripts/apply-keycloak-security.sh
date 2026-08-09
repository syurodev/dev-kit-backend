#!/usr/bin/env sh
set -eu

# Realm import is skipped when the realm already exists. This local helper
# applies the same baseline through the supported Keycloak Admin REST API.
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
container=${KEYCLOAK_CONTAINER_NAME:-devkit-keycloak}
admin_server=${KEYCLOAK_ADMIN_SERVER:-http://127.0.0.1:${KEYCLOAK_PORT:-8081}}

security_admin=${KEYCLOAK_SECURITY_ADMIN_USERNAME:-}
security_client=${KEYCLOAK_SECURITY_ADMIN_CLIENT:-}
security_secret=${KEYCLOAK_SECURITY_ADMIN_SECRET:-}

if [ -z "$security_client" ] && [ -z "$security_admin" ]; then
  security_admin=$(docker exec "$container" sh -c 'printf %s "$KC_BOOTSTRAP_ADMIN_USERNAME"')
fi
if [ -z "$security_secret" ]; then
  security_secret=$(docker exec "$container" sh -c 'printf %s "$KC_BOOTSTRAP_ADMIN_PASSWORD"')
fi

KEYCLOAK_ADMIN_SERVER="$admin_server" \
KEYCLOAK_SECURITY_ADMIN_USERNAME="$security_admin" \
KEYCLOAK_SECURITY_ADMIN_CLIENT="$security_client" \
KEYCLOAK_SECURITY_ADMIN_SECRET="$security_secret" \
python3 "$repository_root/scripts/apply_keycloak_security.py"

unset security_secret
echo "Applied the DevKit security baseline to the existing Keycloak realm."
