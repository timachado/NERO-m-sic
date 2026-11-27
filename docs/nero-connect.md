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

## Local pairing preview

Debug Discovery now exposes manual exchange of P-256 public keys. The private key is generated and retained by Android Keystore; each device independently compares a 48-bit fingerprint of both public keys and explicitly confirms the peer. The app remembers one peer public key locally and can revoke it. This is a local trust record, not proof of remote possession or account ownership. No network, signed challenge, session token, remote command delivery, or control is enabled. Confirmed peers must still prove possession via signed challenges on an authenticated transport before remote commands are accepted. Test key generation, comparison, rejection and revocation with two Android devices. Never treat the offer as a secret or auto-approve a peer.

## Signed possession proof (0.9.39.1-dev)
Manual exchange of a two-minute random challenge and ECDSA signature verifies possession of the previously confirmed peer key. The nonce and both device identities are bound to the signature; the verifier consumes its pending challenge after one attempt. Proof is local to the dialog, not a persisted session grant. Both devices must verify separately. Transport and remote execution remain disabled; next gate is authenticated encrypted transport plus command authorization. Six additional unit tests cover replay, wrong key, wrong identity, expiration and malformed proof.

## Same-Wi-Fi control preview (0.9.39.2-dev)
After manual public key confirmation, the receiver explicitly starts a five-minute foreground-only listener bound to its private IPv4 Wi-Fi address. The sender enters the IP:port displayed by the receiver. Mutual TLS 1.2/1.3 uses the existing Keystore certificate and exact peer public-key pins on both sides, not a public CA or a permissive trust manager. One command per connection with fresh random nonce, bounded read time, rate limit and recheck of peer and network. Only play/pause/previous/next for an already selected queue, subject to Media3 capabilities. Closing the dialog or leaving the foreground stops reception. No seek, queue replacement, remote audio transfer, automatic discovery, relay or external server. The earlier transport-independent command validator remains an unused future protocol. Verify TLS handshake with real Keystore certificates on two physical Android devices, rejects unpaired peers, disconnect/expiry, Wi-Fi isolation, pauses, and queue boundaries before promoting. Four unit tests cover network parsing and key matching; no successful end-to-end device test yet.

## 0.9.108-dev — recuperação isolada para homologação
Código de pareamento, prova P-256 e TLS mútuo recuperado de archive/experimental-0.9.41.3. Identidade e chave local isoladas por UID Google; mudança de conta encerra UI e invalida comandos. Recepção explícita de cinco minutos, primeiro plano, mesma rede Wi-Fi privada; somente play, pause, próxima e anterior conforme capacidade e regra Premium do receptor. Sem modo snapshot antigo, sem relay, sem transmissão de áudio, sem alteração de NERO Together ou do backend. Testes unitários recuperados; handshake real em dois aparelhos, expiração, troca de conta e revogação ainda exigem homologação. Não é recurso comercial validado.


## Native Connect 195 — product acceptance contract

Goal: independent receiver playback, not mirroring and not a single-track handoff. A controller selects an available, authorized receiver; the receiver obtains permitted media directly, owns playback and the queue, and reports confirmed state. The controller sends commands and does not need to keep a media stream running. Device capabilities and provider permissions determine supported actions. No Spotify protocol or credentials are reused.

Acceptance criteria:
- Discover/select a compatible receiver without manual browser navigation; a supported receiver or native playback protocol must actually be available. LG LF5900/webOS 2.0 compatibility is unverified. LG Developer Mode on physical devices starts with webOS 3.0.
- Pair with explicit receiver approval and authenticated encrypted communication. Bind commands to receiver/session identity; enforce replay protection, expiry, owner changes and disconnect.
- Transfer current queue order, current item and permitted position. Enforce Free/Premium control policy for NERO-owned playback, without restricting access to embedded YouTube or charging for that content.
- Pause local playback only after verified receiver PLAYING for the requested session/item. Do not assume launch acknowledgement is playback. Preserve local playback on failed transfer.
- Receiver advances independently while controller sleeps or disconnects; no fake end events, no duplicated advances, no unexpected restart.
- Read actual remote state and volume; distinguish device volume from player volume. Play/pause/next/previous/seek only when supported and authorized. Reconnect obtains receiver state rather than overwriting it.
- On return to local playback, confirm remote stop first and resume at confirmed position where permitted.
- For YouTube use an authorized visible official player with its attribution, ads and functionality preserved; no isolated audio, hidden/background video playback, extracted stream forwarding or claims of Spotify-equivalent licensing. Full audio-only independent playback requires a separately authorized audio source.
- Preserve WVC as the existing fallback. No production database, checkout, release publication or credential changes in this branch.

Validation: first prove receiver availability and independent playback on actual hardware, then implement authenticated control and full end-to-end tests. The hosted receiver and debug launch are experimental, not a commercial Connect service. If the legacy LG has no compatible native receiver, document that limitation rather than fake successful connection.
References:
- https://support.spotify.com/uk/article/Spotify-Connect/
- https://webostv.developer.lge.com/develop/tools/webos-tv-cli-dev-guide
- https://developers.google.com/youtube/terms/developer-policies
