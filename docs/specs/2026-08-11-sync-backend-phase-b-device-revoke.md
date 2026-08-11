# DevKit Sync Backend — Phase B Device Revoke Specification

- Status: Implemented (`feat/phase-b-device-revoke`, `2060f39`)
- Date: 2026-08-11
- Backend repository: `/Users/syuro/Workspace/PERSONAL/dev-kit/dev-kit-backend`
- Desktop: `/Users/syuro/Workspace/PERSONAL/dev-kit/dev-kit-app`
- Canonical design:
  `base-doc/docs/superpowers/specs/2026-08-11-be-phase-b-device-revoke-design.md`
- Depends on: Phase A (`docs/specs/2026-08-09-sync-backend-phase-a.md`)

## 1. Purpose

Phase B (device revoke slice) thêm self-service **list** và **revoke** device
qua HTTP, ghi Redis denylist TTL 60s để Gateway reject sớm, và giữ PostgreSQL
`devices.status` làm nguồn đúng lâu dài. Desktop bridge tối thiểu + G4 dùng
API thay SQL. Không gồm device-management UI polish.

## 2. Goals

1. `GET /v1/sync/devices` — list devices của account từ JWT `sub`.
2. `POST /v1/sync/devices/{deviceId}/revoke` — revoke với:
   - caller ACTIVE;
   - không revoke ACTIVE cuối cùng (`409`);
   - idempotent nếu đã revoked;
   - audit `device.revoked`;
   - xóa pending enrollments `created_by_device_id = target`.
3. Sau commit: Redis `SET sync:revoked-device:{sub}:{deviceId} 1 EX 60`.
4. Gateway: `EXISTS` denylist → `403` trên `/v1/sync/**`; Redis down → fail-open.
5. Compose thêm Redis trên private network.
6. Desktop: bridge/syncclient list+revoke; G4 revoke qua API.

## 3. Non-goals

- Device management UI / 3-pane merge polish
- Keycloak Admin logout
- Rename / reactivate device
- Redis trong correctness path của push/pull arbitration
- Rate limit phân tán, retention/backup engine, multi-region
- Internal JWT `jti` claim / jti denylist

## 4. Why device denylist (not jti)

Gateway mint internal JWT mỗi request và hiện không có `jti`. Blacklist một
`jti` không chặn request kế tiếp của device đã revoke khi Keycloak access token
còn sống. Denylist theo `{sub}:{deviceId}` đóng đúng cửa sổ edge; PG status vẫn
enforce sau khi TTL hết.

## 5. Wire sketch

### List

`GET /v1/sync/devices`

Response:

```json
{
  "devices": [
    {
      "deviceId": "…",
      "status": "active|revoked",
      "createdAt": "…",
      "lastSeenAt": "…",
      "current": true
    }
  ]
}
```

### Revoke

`POST /v1/sync/devices/{deviceId}/revoke`

| Case | Code |
|---|---|
| Unauthenticated / bad protocol | `401` |
| Caller inactive/revoked | `403` |
| Target missing / other account | `404` |
| Last ACTIVE device | `409` |
| Success / already revoked | `200` |

## 6. Redis

- Key: `sync:revoked-device:{sub}:{deviceId}` (`sub` = Keycloak subject)
- TTL: 60 seconds
- Write after successful PG commit only
- Gateway fail-open if Redis unavailable

## 7. Implementation touchpoints (expected)

- `identity/adapter/in/web/` — list/revoke controllers
- `identity/application/` — use cases + `DeviceRepository` list/revoke
- Invalidate enrollments via `DeviceEnrollmentRepository` (extend as needed)
- Redis port/adapter in API; Gateway denylist filter + Redis client
- Compose: Redis service + API/Gateway config
- Tests: SyncApiIT / Gateway IT; desktop `synce2e` G4

## 8. Done when

Matches canonical design “Done when” checklist in
`2026-08-11-be-phase-b-device-revoke-design.md`.

## 9. Implementation evidence (2026-08-11)

- `./gradlew :test --tests com.synx.devkit.e2e.SyncApiIT` → PASS
- `./gradlew :gateway:test` → PASS (denylist filter)
- Desktop (`02afaaa`): unit/bridge tests PASS; G4 rewritten to API revoke
- Deployed matrix `TestG4RevokedDeviceRejected` not re-run this session — re-run
  `go test ./cmd/synce2e/ -run TestG4RevokedDeviceRejected` against Compose when
  verifying
