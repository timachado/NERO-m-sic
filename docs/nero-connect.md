# NERO Connect — development foundation

Status: protocol and local validation only; no remote control enabled.

## Proposed first working milestone

- An authenticated user explicitly enables remote control for a particular paired device.
- Device pairing and revocation are confirmed on the receiving device; an identifier is not a credential.
- Authenticated, encrypted transport binds the account, sender and recipient. Device-specific session credentials remain in Android secure storage.
- Short-lived, monotonically sequenced commands are validated locally. Persist the highest accepted sequence atomically before executing; reject replay, expired or wrong-session messages. Rotate the session when pairing is changed. Use server-validated timestamps and bound clock skew.
- Allow only play, pause, previous, next and bounded seek for the existing Media3 session. Recheck current player capabilities and track identity before execution. Never accept arbitrary URLs, files, shell commands or account changes.
- Explicit disconnect/revoke stops command delivery. No listening or microphone permission for Connect. No opening ports or publicly exposing the player.
- Publish only minimal playback status to the authorized peer, with user-controlled visibility. Do not transfer audio or downloads.
- Later milestones: queue synchronization, device handoff, NERO Together and optional cloud data synchronization.

## Validation gates

1. Preserve the approved playback, offline and library flows; wait for the current Android Auto/Cast regression build.
2. Select and verify a transport and its account isolation. Backend changes require explicit authorization; use isolated staging before production.
3. Implement pairing UI and device authorization; do not expose remote controls until end-to-end authentication and revocation work.
4. Add command idempotency, rate limiting, disconnect and session expiry tests; test with two physical Android devices.
5. Review privacy and battery use. No commercial publication or paid service activation in this branch.

The protocol validates fields, lifetime, expected device, active session and sequence. It is not a substitute for authenticated transport, secure credential storage, or atomic sequence persistence.
