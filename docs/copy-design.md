# Copy between servers (SCRIPT_API 58)

Approved by the owner 2026-11-27. Settings › Connection › Copy. Server-to-server only: never through the phone, secret values never shown.

## Decisions
1. No encryption at rest for the copy file on B (mode 600 in a 700 folder, minutes only; SSH in transit).
2. Secrets kind = launcher `env` (merged by variable NAME: only missing names added, a name with another value is reported as a conflict, never overwritten; then `ensure_env_hook`) + `secret-files/*`. Keystores and Apple keys (`login-keystore`, `login-apple` files) are a separate opt-in tick. NEVER copied: `config`, `migrate/`, `copy/`, push tokens, Claude/worker `.credentials.json` (two servers refreshing one login sign each other out).
3. 20 GB cap (claude-backup receive's cap), no resume.
4. Do NOT raise MIN_SCRIPT_API. The app shows the Copy screen only when BOTH servers report SCRIPT_API >= 58 (`COPY_MIN_SCRIPT_API = 58`, like `QUEUE_MIN_SCRIPT_API`).

## Reuse
- `claude-backup receive` (server/claude-backup, forced command; one stream -> mode 600 file in a 700 folder, size/disk caps). Add `--copy`: names the file `copy-<UTC time>.tgz` in the copy folder.
- From claude-setup.sh migrate code: pinned-host-key ssh + progress loop (`migrate_worker_send`), authorized_keys rewrite (`do_migrate_authorize`), `migrate_is_ip`, status/lock/alive helpers, `migrate_need_run`, `read_secret_line`, key removal from migrate-verify. PARAMETERIZE them (marker, folder, status file) rather than copying: migrate keeps marker `clauderc-migrate`; copy uses `clauderc-copy`, folders `~/.config/claude-launcher/copy` and `$API_STATE_DIR/copy`. migrate-* must never remove a copy key and copy must never remove a migrate key.
- From claude-backup: `dir_list` + junk excludes (node_modules, build, .gradle, target, .cache, __pycache__), `safe_rel`, `repo_state`, the pre-restore copy idea.

## Actions (args fit TOKEN_RE; add to KNOWN and the stdin list)
- `copy-list <project|secrets|bundle> [project]` (read-only, both servers) -> `{entries:[{path,size,mtime,mode}], truncated}`, cap 20k, junk excluded. secrets: env NAMES + `secret-files/` names (+ keystore/apple file names marked `optional:true`), never values. bundle: stdin = source's `name sha` lines; answers which shas B already has (`git cat-file -e`). On the source, the bundle list = branches whose repo_state is no-upstream/no-remote/ahead.
- `copy-keygen` (A): NEW ed25519 key per copy in `copy/key` (600) -> `{public_key, tailnet_ip}`; tailnet_ip from `tailscale ip -4` (read-only), "" if absent.
- `copy-authorize` (B, ALLOW_RUN): stdin = key line, source address (optional), minutes to live (default 120, max 720). Appends exactly one line, replacing an older `clauderc-copy` line:
  `restrict,command="<home>/.local/bin/claude-backup receive --copy",from="<ip>",expiry-time="YYYYMMDDHHMMZ" ssh-ed25519 <blob> clauderc-copy` (`from=` only for a literal IP, like migrate).
- `copy-probe` (A): stdin = host, port, host-key type, blob. `ssh -o BatchMode=yes` with only that pinned key in a temp known_hosts and no identity; "Permission denied" = reachable + key ok -> `{reachable, hostkey_ok}`.
- `copy-send <kind> [project]` (A, ALLOW_RUN): stdin = same five lines as migrate-send. Detached job: tar.gz (`--no-same-owner`, members relative to the project folder or `~/.config/claude-launcher`) + `.clauderc-copy.json` (kind, project, mtimes, modes, chosen paths); streams over ssh; EXIT trap deletes A's private key and the temp tarball, success or failure. Optional stdin after the five lines: the chosen relative paths (one per line; empty = all listed).
- `copy-status` (any server): same shape as migrate-status.
- `copy-apply <copy-file>` (B, ALLOW_RUN): stdin = policy `new-only|newer`, then relative paths the owner agreed to overwrite. Detached: extract into an EMPTY staging folder, check, move file by file; B's version backed up to `~/backups/pre-copy-<time>/` before any overwrite; at the end remove the `clauderc-copy` line and the copy file.
- `copy-cancel` (B): removes the line and the copy file.

## Kinds
- project: folder missing on B -> whole folder incl. `.git`. Present -> only untracked + ignored files (`git ls-files --others`), `.git` never overwritten.
- secrets: see Decisions 2.
- bundle: `git bundle create - <unpushed branches> --not --remotes`; on B fetch into `refs/remotes/from-<A>/*` (local branches never moved); repo missing -> `git clone` from the bundle.

## Key lifecycle
Fresh key per copy on A; the phone relays only the public half. B pins it with forced command + `from=` (tailnet) + `expiry-time`. Removed by copy-apply/copy-cancel on B; A's private half deleted when the send job ends. `do_status` (runs on every app poll) prunes expired `clauderc-copy` lines and copy files older than 24 h.

## Route (app)
Both servers have `tailnetHost`: run copy-probe on A against B's tailnet address (same port, same pinned key). ok -> tailnet, pass tailnet_ip as `from=`. Unreachable -> B's `host`, no `from=`, screen says "public route". Wrong host key -> STOP, no fallback.

## Safety
Project name `^[A-Za-z0-9._-]+$`, not starting with `.`, `realpath -m` under PROJECTS_DIR which is under `$HOME`. After staging every member checked with `safe_rel`; symlink targets must resolve inside the destination root (else refused and listed); no parent dir on the way may be a symlink. Default new-only; files newer on B or differing with the same mtime only overwritten when named. Secrets and copy file 600, folders 700. One copy job per server (own lock). Nothing deleted on A.

## App flow
1 Pick From / To / kind (+ project from A's `repos`). 2 Check: copy-list on both, diff in pure Kotlin -> Missing on B / Newer on A / Newer on B (names only). 3 Choose (Newer on B unticked, needs explicit overwrite; optional secrets unticked). 4 Route: probe -> "Tailnet" or "Public". 5 Send: copy-keygen(A), copy-authorize(B), copy-send(A), poll copy-status(A). 6 Apply: copy-apply(B), poll, result: skipped/refused paths listed, key confirmed removed.

## Tests
authorize writes exactly one line (restrict/command/from/expiry), replaces an older one, leaves migrate lines; apply/cancel/status pruning remove it. Tar members with `../`, absolute, symlink-then-file escape refused. new-only/newer + overwrite list; pre-copy backups exist. env merge never changes an existing value; modes 600/700. bundle fetch doesn't move local branches. A's key gone after failed and successful send. Kotlin unit tests for the diff and the route choice. test-sshd.sh: real sshd honours forced command and `expiry-time` (OpenSSH >= 7.7).
