#!/usr/bin/env sh
set -eu

# This container runs as the database owner and creates a DML-only runtime
# role. psql format(%I/%L) safely quotes both identifiers and passwords.
: "${DEVKIT_DB_HOST:?DEVKIT_DB_HOST is required}"
: "${DEVKIT_DB_NAME:?DEVKIT_DB_NAME is required}"
: "${DEVKIT_DB_ADMIN_USER:?DEVKIT_DB_ADMIN_USER is required}"
: "${DEVKIT_DB_ADMIN_PASSWORD:?DEVKIT_DB_ADMIN_PASSWORD is required}"
: "${DEVKIT_DB_APP_USER:?DEVKIT_DB_APP_USER is required}"
: "${DEVKIT_DB_APP_PASSWORD:?DEVKIT_DB_APP_PASSWORD is required}"

export PGPASSWORD=$DEVKIT_DB_ADMIN_PASSWORD

psql \
  --host "$DEVKIT_DB_HOST" \
  --username "$DEVKIT_DB_ADMIN_USER" \
  --dbname "$DEVKIT_DB_NAME" \
  --set ON_ERROR_STOP=1 \
  --set app_user="$DEVKIT_DB_APP_USER" \
  --set app_password="$DEVKIT_DB_APP_PASSWORD" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

SELECT format(
    'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'app_user', :'app_password')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'app_user')
\gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user')
\gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app_user')
\gexec
SELECT format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user')
\gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
    :'app_user')
\gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I',
    :'app_user')
\gexec
SQL
