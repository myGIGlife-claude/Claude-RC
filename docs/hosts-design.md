# Hosts: servers that are not for running Claude (web servers)

A **host** is a remote server the owner adds in the app (address, user, SSH key or password). Chats can be
**attached** to a host (the 👥 sheet, like workers); the chat's Claude then runs commands on it and copies files to it,
working in **its own folder** on the host. Nothing is installed on the host and Claude is not run there.

## Security rules (do not weaken)
- The private key never leaves the main server's private folder (mode 600) and is never shown again, logged or put in an
  environment variable. Passwords are used once, then discarded: the server makes its own ed25519 key, installs the public
  half with the password, checks that key login works, and drops the password. Nothing stores a password.
- Host key is pinned: `host-probe` returns the fingerprint, the owner confirms it in the app, `host-add` refuses if the
  fingerprint it sees now differs. All later ssh uses `StrictHostKeyChecking=yes` with the host's own `known_hosts`.
- Every ssh uses `BatchMode=yes`, `IdentitiesOnly=yes`, `PasswordAuthentication=no`, `-i <that host's key>` and a connect timeout.
- Names/addresses/users are validated (see below); no value ever goes through a shell: argv lists only.
- Attach is a convenience filter, not isolation: any session can read `hosts/<name>/key` itself.
- Attach is a guardrail, not a sandbox: sessions run as the same Unix user as the launcher. Say so in docs; do not claim isolation.
- Public repo: no personal values (addresses, users, key material) in code, tests or docs.

## Storage (`$LAUNCHER_CONFIG_DIR`)
- `hosts/<name>/meta.json` `{address, port, user, fingerprint, auth:"key"|"password"|"generated", added}`; `hosts/<name>/key` (600); `hosts/<name>/known_hosts` (600). `hosts/` is 700.
- `hosts/.keygen-<name>/key` (dir 700, key 600): a key from `host-keygen` waiting for `host-add … generated`. Removed when that add succeeds, or after 24 h unused.
- Attach state per project: `attach/<project-slug>.hosts.json` = `{"<host>": {}}` (a separate file from the workers' attach file,
  which clauderc-team iterates by key). Reuse the slug logic of `attach_session`/`claude_proj_slug`.

## Validation
- name: `^[a-z][a-z0-9-]{0,29}$` (lower case, shown in the app and used in tools).
- address: hostname `^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$` or IPv4/IPv6 literal; port 1-65535; user `^[a-z_][a-z0-9_-]{0,31}$`.
- fingerprint: `^SHA256:[A-Za-z0-9+/]{43}$`.

## Server API (claude-setup.sh `--api`, allowlisted in claude-launcher-api; SCRIPT_API 39, `host-keygen`/`generated` 40)
All replies are the usual `{ok, data | error}`. Secrets come on stdin one per line (`read_secret_line`), never in argv.
- `host-list` → `{hosts:[{name,address,port,user,auth,fingerprint,added}]}` (no key material).
- `host-probe` stdin: address, port → `{fingerprint, keytype}` via `ssh-keyscan` + `ssh-keygen -lf`, no login. Error if unreachable.
- `host-keygen <name>` (no stdin) → `{public_key, fingerprint}`: an ed25519 pair (comment `clauderc-host-<name>`) in `hosts/.keygen-<name>/`,
  the same one again if it is already there. Only the public half is returned. Refused if a host with that name exists. For providers
  that ask for a public key when they install the server (e.g. OVH), or to append to the user's `~/.ssh/authorized_keys` by hand.
- `host-keyimport <name>` (SCRIPT_API 42) stdin: the owner's own private key, base64 on one line → `{public_key:"<type> <base64>", fingerprint}`.
  Validated like `host-add … key` (base64, `ssh-keygen -y -P ''`; passphrase keys refused). Stored as the pending key
  `hosts/.keygen-<name>/key` (replacing any), with a marker file `imported` so a later `host-keygen` makes a new key instead of returning it.
  Refused if a host with that name exists. Only the public half is returned; then `host-add … generated` uses it.
- `host-add <name>` stdin: address, port, user, fingerprint, auth (`key`|`password`|`generated`), secret.
  - `generated`: secret is any placeholder (e.g. `-`), ignored. Uses `hosts/.keygen-<name>/key` ("Generate or import the key first" if missing).
    If key login fails the pending key stays (the error says to add the public key to authorized_keys), so the owner fixes the server and saves again.
  - `key`: secret is the private key, base64 (one line, no newlines). Reject unless `ssh-keygen -y` can read it; passphrase-protected keys are refused with a clear message.
  - `password`: generate `ed25519` key, install the public key (use `SSH_ASKPASS` with a 600 temp script that prints the password and `SSH_ASKPASS_REQUIRE=force`, or `sshpass -e` if installed; the password must not appear in argv or logs), then verify key login. Discard the password; set `auth:"password"` only to mean "key was installed with a password".
  - Both: pin the host key from probe (must match fingerprint), verify `ssh <host> true` works, then write the files. Nothing is saved if verification fails. An existing name is refused (remove it first), so attachments never silently point at a different server.
  → `{saved:name}`.
- `host-test <name>` → `{ok:true}` or error with ssh's last line. `host-remove <name>` → deletes `hosts/<name>` and every project's attachment (does not touch the remote's authorized_keys: say so in the app).
- `host-session <project>` → `{hosts:[{name,address,user,attached}]}`; `host-attach <project> <name> on|off` → `{host,state}`.
- `host-list` and `host-session` must never include secrets; the api log must never contain secrets.

## Session tools (server/clauderc-team MCP, available when the chat's project has attachments)
- `list_hosts` → attached hosts (name, address, user, workspace path).
- `host_run(host, command, timeout=120)`: runs `command` through the remote shell with cwd = the chat project's own folder
  `~/sites/<project-slug>` on the host (created on first use with `mkdir -p`). Returns exit code, stdout, stderr (cap ~100 KB each).
  Only for hosts attached to this chat's project; otherwise an error telling the owner to attach it with 👥.
- `host_put(host, local_path, remote_path)` and `host_get(host, remote_path, local_path)`: scp/rsync-style copy; `remote_path` is
  relative to the workspace folder (reject absolute paths and `..`); `local_path` must be inside the chat's project dir.
- Add a short "hosts" paragraph to the tool instructions: work only in your own folder, use the deploy user (no root), tell the owner what you changed.
- ssh is built from argv lists, never a shell string, and uses the safe options above.

## App
- Cluster tab (ClusterSection): a "Hosting servers" card: list, Test, Remove (confirm; says the key stays authorized on the server until removed there), **+ Add host**.
- Add host dialog: name, address, port (22), user → **Check** (host-probe) shows the fingerprint to confirm → pick Key (paste, or upload a file; base64 on the wire), Password, or Generate a key (host-keygen; shows the public key with Copy and where to put it) → Save (host-add). Error text from the server is shown in the dialog (not only in a snackbar).
- Chat 👥 sheet: a "Servers" section with a switch per host (host-session / host-attach).
- Needs SCRIPT_API 42 (host-keyimport; 40 = Generate a key, 39 = neither): raise `Updates.MIN_SCRIPT_API`.

## Set up and harden (SCRIPT_API 41)

Researched sources: Ubuntu sshd drop-ins are read in lexical order and the FIRST value wins, and cloud images ship
`50-cloud-init.conf` with `PasswordAuthentication yes`, so ours must sort first (`00-clauderc.conf`) and be checked with
`sshd -T`; Docker's published ports bypass UFW (they go through FORWARD, never INPUT), the supported hook is the
`DOCKER-USER` chain, and the simple rule for sessions is to publish container ports on `127.0.0.1` behind Caddy.

### API: `host-harden <name>`
stdin: line 1 = ports to open (comma separated, e.g. `80,443,8080/tcp,51820/udp`, may be empty; each `^[0-9]{1,5}(/(tcp|udp))?$`, 1-65535,
max 20; default proto tcp), line 2 = steps (comma separated from `harden`, `optimize`, `web`; may be empty = only open the ports).
Runs over the host's pinned ssh as `sudo -n bash -s` with a script embedded in claude-setup.sh (a heredoc function; do NOT add
files to install.sh). Needs passwordless sudo or root: check `sudo -n true` first, else error `needs_sudo` ("this user needs
passwordless sudo, or use root"). Server timeout 540 s. Reply `{ssh_port, docker, steps:[{name, status:"ok"|"skipped"|"failed", detail}]}`.
The remote script prints `STEP <name> <ok|skipped|failed> <detail>` lines which the server parses; idempotent (safe to re-run).

**Lock-out protection (required):** before touching sshd/ufw, the script schedules a revert with `systemd-run --on-active=120 --unit=clauderc-revert`
(removes the sshd drop-in, reloads ssh, `ufw disable` only if ufw was inactive before the run). After the apply call returns, the server opens a NEW ssh login with the host's key
(like host-test); only if that works does it cancel the revert (`systemctl stop clauderc-revert.timer clauderc-revert.service`) in a second call.
If the new login fails, wait for the revert (about 2 minutes), re-test, and return error `locked_out_reverted` explaining nothing was left locked.
If the revert service is running at check time it is not stopped: wait for it, sign in again (fails → `locked_out_reverted`) and report a failed
`safety` step saying the undo ran.
As built: the settings go after sudo (`sudo -n env CLAUDERC_…=… bash -s`, sudo resets the environment; root runs without sudo); the slow
apt/optimize/web work runs BEFORE the timer is set, so only the quick ufw/sshd part is inside the 2 minutes; the fresh login itself stops the
timer; the port sshd answered on (`SSH_CONNECTION`, differs behind a port forward) and every `port` from `sshd -T` are allowed too. Bad ports/steps → `invalid_name`.

**Steps (order matters):**
- always: ports. `ufw allow <ssh_port>/tcp` FIRST (the port the app connects on), then the requested ports, then `ufw --force enable`.
- `harden`: apt update; install ufw fail2ban unattended-upgrades; `ufw default deny incoming`, `default allow outgoing` (ssh ports plain `allow`, never `ufw limit`: it would drop this server's many short ssh connections; fail2ban does the brute-force part), IPv6 on (`IPV6=yes` in /etc/default/ufw);
  sshd drop-in `/etc/ssh/sshd_config.d/00-clauderc.conf`: `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PubkeyAuthentication yes`,
  `PermitRootLogin no` (use `prohibit-password` if the login user IS root), `MaxAuthTries 3`, `LoginGraceTime 30`, `X11Forwarding no`, `AllowAgentForwarding no`,
  `ClientAliveInterval 300`, `ClientAliveCountMax 2`; validate with `sshd -t` (remove the file and report failed if invalid), confirm every value with `sshd -T`
  (password/kbd-interactive off, root login, pubkey on; any that differs → failed naming it),
  reload (`systemctl reload ssh || systemctl reload sshd`; under ssh.socket "saved, sshd will read it on the next connection");
  `/etc/fail2ban/jail.d/clauderc.local` ([sshd] enabled, `backend = systemd`, `port = <ssh ports>`,
  `ignoreip = 127.0.0.1/8 ::1 <this server's IP from SSH_CONNECTION, if a valid IP>`,
  `maxretry = 4`, `findtime = 10m`, `bantime = 1h`, `bantime.increment = true`) then restart fail2ban; unattended-upgrades enabled for security
  (`/etc/apt/apt.conf.d/20auto-upgrades`, no automatic reboot); `/etc/sysctl.d/99-clauderc.conf` with only Docker/Caddy-safe values (tcp_syncookies=1,
  rp_filter=1 all/default, accept_redirects=0, send_redirects=0, accept_source_route=0, log_martians=1, icmp_echo_ignore_broadcasts=1, kernel.dmesg_restrict=1,
  kernel.kptr_restrict=2, fs.protected_hardlinks=1, fs.protected_symlinks=1) — never touch `net.ipv4.ip_forward`; `sysctl --system`.
  If Docker is installed, return `docker:true` and a step detail warning that published ports bypass UFW (publish on 127.0.0.1)
  and that `default deny incoming` can block containers reaching host services through the docker0 bridge.
- `optimize`: time sync on (`timedatectl set-ntp true`), journald cap (`/etc/systemd/journald.conf.d/clauderc.conf` SystemMaxUse=200M), swap file only when there is
  no swap and RAM <= 2 GB (1 GB `/swapfile`, mode 600, fstab entry, `vm.swappiness=10`). No `apt-get autoremove` (too aggressive for a default step).
- `web`: Caddy from its official apt repo (keyring in /usr/share/keyrings, `deb ... stable main` list, apt install caddy) plus git, curl, unzip, build-essential; ports 80/443 are NOT opened unless requested.
- Non-interactive apt: `DEBIAN_FRONTEND=noninteractive`, `-y -o Dpkg::Options::=--force-confold`. Never use `ufw reset`, never change the SSH port, never lock the root password.

### Session tool: `host_firewall(host, action, port?, proto?)` in clauderc-team
`action` status|open|close. open/close run `sudo -n ufw allow|delete allow <port>/<proto>` over the host's ssh (validated port 1-65535, proto tcp|udp);
refuse to close the host's SSH port (the app's port, and on the host itself the port in `SSH_CONNECTION` and every `sshd -T` port:
exit 3 "that is an SSH port"); `status` returns `ufw status numbered`. Same attach gate as host_run. Mention in the hosts instructions paragraph:
open only the ports the project needs, publish Docker ports on 127.0.0.1 behind Caddy, ask the owner before opening anything unusual.

### App
- Add host dialog: the **public key generator is at the TOP**, right under Name (needs only a valid name): "Need a public key for your provider first (e.g. OVH)?" → **Generate new key** or **Use existing key** (paste the private key, hidden, or pick a file → `host-keyimport`) → the public key and fingerprint shown with Copy; the address/user fields and Check come after. Once a key is shown, the sign-in choice defaults to "Key above" (auth `generated`, the pending key). Changing the name clears it.
- After Save succeeds, the dialog moves to a **Set up this server** step: switch "Harden the server (recommended)" (on), switch "Tune it (time sync, log size, swap)" (on), switch "Install web basics (Caddy, git, build tools)" (off), port checkboxes: SSH (always on, shows the port), HTTP 80 (on), HTTPS 443 (on), plus a "More ports" text field (comma separated, `8080` or `51820/udp`, validated client side). Buttons: **Set up** (runs host-harden; busy indicator; shows the steps list with ✓/–/✗ and details; shows `needs_sudo` / `locked_out_reverted` errors in the dialog) and **Skip**.
- Each host row also gets a **Set up / harden** button that opens the same step (idempotent re-run).
- Needs SCRIPT_API 41: `Updates.MIN_SCRIPT_API = 41`.
