# NERO AI 2.0 — development plan

Baseline: approved Android RC 228 (1.2.7). Commercial release is a separate approval and publication operation. No production changes or paid-provider subscriptions are part of this branch.

## NERO Identify
A foreground, one-tap recognition screen and optional home shortcut. Ask for RECORD_AUDIO only when the user taps Identify, explain the transmission of a short environmental audio sample, and stop immediately on exit/cancellation. No continuous listening, microphone service, raw audio persistence, or automatic recording. Never require interaction while driving.

The initial encoder records 8 seconds of mono PCM at 16 kHz into memory, maximum WAV payload 256044 bytes. The sample encoder has no network or UI entry point yet. Verify recording permissions, physical capture, timeout, interruption, denial and cleanup on Android before enabling.

Provider candidate: AudD standard music recognition (https://docs.audd.io/ ; https://audd.io/pricing/). Verify commercial terms, trial availability, billing and audio retention before activation. Use a dedicated authenticated HTTPS backend, not a provider token embedded in the APK. Proposed POST /v1/identify accepts audio/wav up to 256044 bytes and returns a normalized match (artist, title, album, optional verified IDs/artwork), no match, quota exhausted, or unavailable. Validate server identity, limit payload and per-account requests, global cost ceiling, no audio logs, no sample retention, reject anonymous requests. Use existing identity and entitlement checks; no client-supplied premium flags. Keep integration disabled until configured and tested. Results open existing NERO search; match exact artist/title/version where available; user confirms ambiguous results and saves through existing Quick Save. Recognition history opt-in, account scoped, metadata only; delete controls.

## NERO AI Lyrics
RC 228 already supports user-imported TXT/LRC, embedded lyrics and exact artist/title/duration matching for a debug-only LRCLIB lookup. Preserve these paths. Do not enable debug search indiscriminately in commercial releases. Evaluate a licensed lyrics provider (https://docs.musixmatch.com/lyrics-api). Commercial display, sync and translation require appropriate contractual rights. If unavailable, provide honest missing-letter state and import controls. Transcription only of own or permission-cleared audio supplied for that purpose; label approximate transcription and allow correction. No fabricated song lyrics or extraction of protected streaming audio. No automatic media uploads.

## NERO AI Assistant
Reuse NeroIntelligence, DNA, playlist recommendations, Quick Save, existing account entitlement and player queue. Natural-language request -> structured musical intent -> real catalog search -> confirmed candidate playlist -> preview/play/save. Do not fabricate tracks or silently alter playlists. Consider a rules-based MVP and a server-side generative provider later; describe accurately. Voice commands require explicit microphone consent and separate testing. No provider credentials in source or APK. Limit quota on server, separate cost control from subscription. Proposed Free trial allowance and Premium expanded allowance are not fixed until cost evaluation.

## Acceptance gates
1. Separate integration branch; no commercial publication of unfinished features.
2. Unit tests and signed RC, then real-device recognition from ambient radio, car audio while parked, noise, silence and ambiguous results; cancel and permission denied.
3. Verify lookup, playback and Quick Save without duplicating existing library functionality.
4. Verify lyrics exact version and timestamps, no hallucinated verses, unavailable fallback and offline/local import.
5. Verify account switch, entitlement, quota, privacy, and no paid activation without owner approval.
6. Preserve existing checkout, license, Supabase production, and approved functions.

## Client integration progress
A feature-branch Compose screen and cancellable HTTPS transport now target the existing configured NERO Supabase project at `/functions/v1/nero-identify`, with the existing user JWT and publishable key. Build flag `NERO_IDENTIFY_ENABLED` defaults to false. No server function, provider token, quota allocation, production database changes or activation have been deployed. The screen only requests microphone access after explicit consent and does not start automatically after permission grant. Recognition result links to existing catalog search. The release-candidate workflow is unchanged. Before activating, verify backend authorization, cost limits and audio retention contract. Android compilation and device validation remain pending.

## Authorized isolated pilot deployment
Supabase project `dwpcddiramxlhavdmmyn`: migration `nero_identify_isolated_pilot_v1` applied; function `nero-identify` v1 ACTIVE. Two new RLS tables and a service-role-only reservation RPC, no existing data changes. Verified anon/authenticated cannot read either table or execute reserve, service role can reserve, cooldown rejects repeated attempts, transaction rolled back; counters remain zero. Security advisor informational no-policy notice is intentional for server-only tables. Edge function validates user JWT through Auth API itself, with gateway legacy JWT verification disabled. No live provider request yet; allowlist secret `NERO_IDENTIFY_PILOT_UIDS` must contain the approved NERO test account UID. Keep Android build flag disabled until pilot configuration, privacy/retention review and live test. No paid service activation or commercial publication.

## Build 229 — device pilot acceptance
Signed RC 1.2.8, build 229, source commit `a764403c9eae85e44c8f04079fe2a6cc11c46d09`; workflow run 46174371040 passed build, release lint, unit tests, signing verification and artifact upload (artifact 15274693315). Owner explicitly approved all six Android acceptance checks: install/sign-in, home entry, microphone permission, ambient music playback, identification of track and artist, and catalog search. Read-only database verification confirmed one reserved attempt globally and one for the designated pilot account; counters do not independently prove the recognition result. No public release approved. Keep private allowlist and pilot quota unchanged. Next: scoped recognition history and lyrics-provider evaluation, then commercial access/cost controls before expansion.
