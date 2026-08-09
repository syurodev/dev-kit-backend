#!/bin/sh
# Restrict the Docker-published Keycloak port to K3s pods only.
# Docker routes published ports before UFW's normal INPUT chain, so the filter
# belongs in DOCKER-USER. --ctorigdstport keeps the rule stable after Docker
# translates host port 8181 to the container's HTTP port 8080.
set -eu

readonly K3S_POD_CIDR="${K3S_POD_CIDR:-10.42.0.0/16}"
readonly KEYCLOAK_HOST_PORT="${KEYCLOAK_HOST_PORT:-8181}"

if ! command -v iptables >/dev/null 2>&1; then
  echo "iptables is required to restrict the DevKit Keycloak port" >&2
  exit 1
fi

if ! iptables -w -S DOCKER-USER >/dev/null 2>&1; then
  echo "DOCKER-USER chain is unavailable; start Docker before this service" >&2
  exit 1
fi

allow_rule="-p tcp -s ${K3S_POD_CIDR} -m conntrack --ctorigdstport ${KEYCLOAK_HOST_PORT} -j ACCEPT"
drop_rule="-p tcp -m conntrack --ctorigdstport ${KEYCLOAK_HOST_PORT} -j DROP"

# Insert the drop rule first, then place the CIDR allow above it. Re-running
# the script is safe because exact existing rules are detected with -C.
if ! iptables -w -C DOCKER-USER ${drop_rule} >/dev/null 2>&1; then
  iptables -w -I DOCKER-USER 1 ${drop_rule}
fi

if ! iptables -w -C DOCKER-USER ${allow_rule} >/dev/null 2>&1; then
  iptables -w -I DOCKER-USER 1 ${allow_rule}
fi
