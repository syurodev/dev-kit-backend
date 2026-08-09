#!/usr/bin/env sh
set -eu

# The host private key remains mode 0600. This short-lived init container
# stages a copy owned by the non-root gateway UID into a private named volume.
install -m 0400 -o 10001 -g 10001 /source/private.pem /target/private.pem
install -m 0444 -o 10001 -g 10001 /source/public.pem /target/public.pem
