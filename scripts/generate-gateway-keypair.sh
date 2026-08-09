#!/usr/bin/env sh
set -eu

# This helper is only for local development. Production should mount a keypair
# provisioned and rotated by the deployment secret manager.
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
target_directory=${1:-"$repository_root/.local/gateway-keys"}
private_key="$target_directory/private.pem"
public_key="$target_directory/public.pem"

mkdir -p "$target_directory"
umask 077

# Never overwrite one side of an existing pair because that would invalidate
# tokens and could accidentally publish a mismatched public key.
if [ -e "$private_key" ] || [ -e "$public_key" ]; then
  echo "Gateway key file already exists in $target_directory; nothing changed." >&2
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$private_key"
openssl pkey -in "$private_key" -pubout -out "$public_key"
chmod 600 "$private_key"
chmod 644 "$public_key"

echo "Generated local gateway keypair in $target_directory"
