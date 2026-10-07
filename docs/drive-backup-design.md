# Google Drive backup (phase 1: server -> Drive, app settings -> encrypted file)

Goal: if the phone or the server is lost, the owner can get everything back. The Google login reuses the YouTube device-code
pattern (no Android client, no SHA-1): an OAuth client of type "TVs and Limited Input devices" in the owner's Google Cloud project,
scope `https://www.googleapis.com/auth/drive.appdata` ONLY (the hidden per-app folder: no access to the owner's other files, no
Google review needed). Native in-app Google sign-in is a later phase.

## Credentials
`GOOGLE_DRIVE_CLIENT_ID` and `GOOGLE_DRIVE_CLIENT_SECRET` are custom keys the owner adds in the app (+ > Custom API key). They reach the
actions through the env (read them like the other services do: `${GOOGLE_DRIVE_CLIENT_ID:-}`; if missing, return a clear error
`not_configured`-style message naming the two variables). The refresh token is stored in `$LAUNCHER_CONFIG_DIR/drive-token.json`
(mode 600), never in env, argv or logs. Access tokens are fetched per run from the refresh token and never stored.

## Server actions (claude-setup.sh `--api`; SCRIPT_API 43; allowlisted in claude-launcher-api; follow do_youtube_login_start/poll)
- `drive-login-start` (no stdin): device-code request with scope drive.appdata; pending state file 600; reply `{url, code, interval, expires_in}`.
- `drive-login-poll`: `{pending:true}` until approved; then stores the refresh token and replies `{connected:true}`. Google errors become clear messages.
- `drive-status`: `{configured, connected, schedule:"off"|"daily", last_backup:{name,size,time}|null, has_passphrase}` (no secrets).
- `drive-passphrase` (stdin: the passphrase, 12-200 chars, one line): saves it mode 600 in `$LAUNCHER_CONFIG_DIR/drive-backup-pass` (needed so scheduled backups
  can run). Backups are encrypted with it (claude-backup `--pass-file`), so Google never holds a readable backup. The owner must remember it for a restore.
- `drive-backup` (starts a background run, returns at once `{started:true}`; refuses if one runs, if not connected, or if no passphrase): runs
  `claude-backup export --slim --no-claude-login`? NO: include the Claude logins too (the point is full recovery): `claude-backup export --slim --out <tmp> --pass-file <pass>`,
  then uploads the file to Drive appDataFolder with a RESUMABLE upload using curl (streams from disk, any size; name `clauderc-<host>-<UTC time>.tar.gz.gpg`),
  deletes the local temp file, then keeps only the newest 3 backups in Drive (list + delete older). Progress/result in `$STATE_DIR/drive-backup.json`
  `{state:"running"|"done"|"failed", phase:"export"|"upload"|"cleanup", percent?, message, started, finished, name, size}`. Must work with a stub in tests (`curl`/`claude-backup` stubs).
- `drive-backup-status`: returns that JSON (or `{state:"idle"}`).
- `drive-list`: backups in Drive: `[{id,name,size,time}]` newest first.
- `drive-delete <id>`: deletes one backup (id validated `^[A-Za-z0-9_-]{10,80}$`).
- `drive-schedule on|off`: daily at a quiet hour via the user's crontab (add/remove ONE marked line; create the crontab if missing; idempotent) running `claude-setup.sh --api drive-backup`-equivalent
  (a small wrapper is fine). Do not require sudo.
- `drive-logout`: revokes the token at Google (best effort), deletes drive-token.json, schedule off. Keeps the passphrase file unless `drive-passphrase` is called with an empty value (then it deletes it).
- `drive-restore <id>` (stdin: nothing; uses the saved passphrase; refuses if not connected): downloads the backup to `~/backups/` (700, file 600, never overwrite) and replies the path and the exact command
  to finish: `claude-backup import <path> --pass-file ...`. It does NOT import by itself (import replaces files; the owner runs it, or the migrate wizard does).
All long calls must not block the API for more than ~30 s except where noted; the app polls drive-backup-status.

## App
- Settings (or Connection) > "Google Drive backup" card: not configured -> shows the two variable names and a button to open Custom API keys;
  configured -> Connect (shows the link + code like the YouTube sign-in, polls drive-login-poll), then: Backup passphrase (set/change), Back up now with live progress,
  Daily switch (drive-schedule), list of backups with size/date and Delete, Restore (shows the command to run), Disconnect (confirm).
- Settings > "Back up this app" : Export encrypted file / Import. Content = the app's own settings: servers list and settings from SettingsStore, the SSH key (SshKeyManager) and the chat PIN-vault? NO PIN. Only
  settings + servers + SSH key. Encrypt with AES-256-GCM, key from the owner's passphrase (PBKDF2WithHmacSHA256, 210_000 iterations, random 16-byte salt, random 12-byte IV), format `CLRCBK1` magic + salt + iv + ciphertext.
  Export via ActivityResultContracts.CreateDocument (so it can be saved straight into Google Drive or anywhere); Import via OpenDocument, then it asks for the passphrase, and restoring REPLACES
  the servers/settings/key after a confirm. Never log or show the key. Needs SCRIPT_API 43 only for the Drive card.
