#!/bin/sh
# Restrict the Docker-published DevKit PostgreSQL port to K3s Pods only.
# Docker bypasses UFW's normal INPUT chain for published ports, so filtering
# must happen in DOCKER-USER before host port 5433 is forwarded to PostgreSQL.
set -eu

readonly K3S_POD_CIDR="${K3S_POD_CIDR:-10.42.0.0/16}"
readonly DEVKIT_DB_HOST_PORT="${DEVKIT_DB_HOST_PORT:-5433}"

if ! command -v iptables >/dev/null 2>&1; then
  echo "iptables is required to restrict the DevKit PostgreSQL port" >&2
  exit 1
fi

if ! iptables -w -S DOCKER-USER >/dev/null 2>&1; then
  echo "DOCKER-USER chain is unavailable; start Docker before this service" >&2
  exit 1
fi

allow_rule="-p tcp -s ${K3S_POD_CIDR} -m conntrack --ctorigdstport ${DEVKIT_DB_HOST_PORT} -j ACCEPT"
drop_rule="-p tcp -m conntrack --ctorigdstport ${DEVKIT_DB_HOST_PORT} -j DROP"

# The allow rule is inserted above the drop rule and both checks make reruns
# idempotent. This is intentionally narrower than a UFW rule or 0.0.0.0 allow.
if ! iptables -w -C DOCKER-USER ${drop_rule} >/dev/null 2>&1; then
  iptables -w -I DOCKER-USER 1 ${drop_rule}
fi

if ! iptables -w -C DOCKER-USER ${allow_rule} >/dev/null 2>&1; then
  iptables -w -I DOCKER-USER 1 ${allow_rule}
fi
