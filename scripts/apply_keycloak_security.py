#!/usr/bin/env python3
"""Apply the local DevKit realm baseline without logging credentials or tokens."""

import json
import os
import urllib.error
import urllib.parse
import urllib.request


SERVER = os.environ.get("KEYCLOAK_ADMIN_SERVER", "http://127.0.0.1:8081").rstrip("/")
USERNAME = os.environ.get("KEYCLOAK_SECURITY_ADMIN_USERNAME", "")
CLIENT_ID = os.environ.get("KEYCLOAK_SECURITY_ADMIN_CLIENT", "")
SECRET = os.environ.get("KEYCLOAK_SECURITY_ADMIN_SECRET", "")


def admin_token() -> str:
    if not SECRET or (not USERNAME and not CLIENT_ID):
        raise RuntimeError("Keycloak admin identity and secret are required")
    form = {"grant_type": "client_credentials", "client_id": CLIENT_ID, "client_secret": SECRET}
    if not CLIENT_ID:
        form = {
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": USERNAME,
            "password": SECRET,
        }
    request = urllib.request.Request(
        SERVER + "/realms/master/protocol/openid-connect/token",
        data=urllib.parse.urlencode(form).encode(),
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)["access_token"]
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Keycloak admin login failed with HTTP {error.code}") from None


TOKEN = admin_token()
HEADERS = {"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"}


def request(path: str, method: str = "GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    call = urllib.request.Request(SERVER + path, data=data, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(call, timeout=10) as response:
            body = response.read()
            return None if not body else json.loads(body)
    except urllib.error.HTTPError as error:
        # Do not echo response bodies because an IdP error may contain details
        # that should not be copied into CI logs.
        raise RuntimeError(f"Keycloak admin request failed: HTTP {error.code} {path}") from None


realm_path = "/admin/realms/devkit"
realm = request(realm_path)
realm.update(
    {
        "accessTokenLifespan": 120,
        "verifyEmail": True,
        "bruteForceProtected": True,
        "permanentLockout": False,
        "maxFailureWaitSeconds": 900,
        "minimumQuickLoginWaitSeconds": 60,
        "waitIncrementSeconds": 60,
        "quickLoginCheckMilliSeconds": 1000,
        "maxDeltaTimeSeconds": 43200,
        "failureFactor": 5,
        "passwordPolicy": (
            "length(12) and digits(1) and upperCase(1) and lowerCase(1) "
            "and specialChars(1) and notUsername and notEmail and passwordHistory(5)"
        ),
    }
)
request(realm_path, "PUT", realm)

action_path = realm_path + "/authentication/required-actions/CONFIGURE_TOTP"
action = request(action_path)
action["enabled"] = True
action["defaultAction"] = True
request(action_path, "PUT", action)

# Recovery principals are optional and are removed only when explicitly
# requested. The active client/user is deleted last so this token remains valid.
if os.environ.get("KEYCLOAK_DELETE_SECURITY_ADMIN", "false").lower() == "true":
    cleanup_users = [os.environ.get("KEYCLOAK_CLEANUP_TEMP_USERNAME", "")]
    cleanup_clients = [
        os.environ.get("KEYCLOAK_CLEANUP_TEMP_CLIENT", ""),
        os.environ.get("KEYCLOAK_CLEANUP_TEMP_CLIENT_2", ""),
    ]
    if CLIENT_ID:
        cleanup_clients.append(CLIENT_ID)
    elif USERNAME:
        cleanup_users.append(USERNAME)

    for username in dict.fromkeys(filter(None, cleanup_users)):
        query = urllib.parse.urlencode({"username": username, "exact": "true"})
        for user in request("/admin/realms/master/users?" + query):
            request("/admin/realms/master/users/" + user["id"], "DELETE")
    for client_id in dict.fromkeys(filter(None, cleanup_clients)):
        query = urllib.parse.urlencode({"clientId": client_id})
        for client in request("/admin/realms/master/clients?" + query):
            request("/admin/realms/master/clients/" + client["id"], "DELETE")
