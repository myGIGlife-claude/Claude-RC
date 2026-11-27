# New server: setup, layout and what is left

Written 2026-11-27 so a fresh chat on the new server can carry on. No addresses, user names or secrets are in this file on purpose
(public repo): the new server's address is in the app's server list and `~/.config/claude-launcher/config`; ask Jonathan if unsure.

## What this server is for
1. cLaudeRC: Claude sessions, workers and the app's server scripts (40 GB RAM reserved).
2. A GitHub Actions self-hosted runner for builds and tests (NOT set up yet, see "Decisions").
3. An Android emulator farm: headless emulators for screenshots, screen recordings and UI tests (the Mac stays for iOS).
Web hosting and email stay on their own separate servers. Minecraft is NOT hosted here (it launches on its own server).

## Hardware and OS
12 vCPU (KVM guest, nested virtualization on, `/dev/kvm` works), 92 GB RAM, 1.9 TB disk, Ubuntu 26.04 LTS, one public IPv4 + IPv6, no GPU
(software rendering only: fine for screenshots and short video, slow for 3D). Old server (AWS, 8 vCPU / 30 GB) is still running everything
until Jonathan says to sign it out.

## Done (verified 2026-11-26/27)
- Locked down: key-only SSH (`/etc/ssh/sshd_config.d/00-hardening.conf`: no root login, no password login, MaxAuthTries 3), `ufw` (22/tcp only),
  `fail2ban`, unattended-upgrades, 8 GB swapfile. Main user has sudo WITH a password; that password is only in
  `~/.config/claude-launcher/sudo-password` (mode 600) and `~/.config/claude-launcher/askpass` feeds `SUDO_ASKPASS`. Never print it.
  The old root password was pasted in a chat: Jonathan was told to change it (root login is off anyway).
- cLaudeRC: Claude Code, scripts (SCRIPT_API 55), knowledge pack (41 skills + rules file), Bun, `claude-autostart` (boot service), Node 22 + npm,
  `uv`, `gh` (login copied from the old server without printing it), Docker 29, ffmpeg, OpenJDK 21, ripgrep, bubblewrap.
- Memory/CPU plan (systemd drop-ins in `/etc/systemd/system/`): `user.slice` and the main user's slice `MemoryMin=40G` (the Claude reservation),
  main user `CPUWeight=300`; the emulator user's slice `MemoryHigh=18G`, `MemoryMax=20G`, `CPUQuota=500%`.
- Emulator: user `emu` (kvm group), Android SDK in `~emu/android-sdk`, AVD `pixel35` (Pixel 7, Android 15 google_apis x86_64, 4 GB RAM, 4 cores),
  service `emulator@<avd>` (headless, swiftshader_indirect, no snapshot, `MemoryMax=8G`, fixed port 5554, so ONE instance at a time until the unit
  gets per-instance ports). Tested: boot ~2 min, `adb exec-out screencap -p` (1080x2400 PNG), `screenrecord` (h264). Not left running.
  Use: `sudo systemctl start emulator@pixel35`, wait for `adb shell getprop sys.boot_completed` = 1 as user `emu`
  (`sudo -u emu env PATH=~emu/android-sdk/platform-tools:$PATH adb ...`), then `systemctl stop` when done (rule: never leave an emulator running).
  A stale orphan emulator makes the unit fail with "Running multiple emulators with the same AVD": kill it and remove `*.lock` in the AVD folder.
- Migrated from the old server with `claude-backup` (7.7 GB, 3.5 GB encrypted): projects, `~/.claude` (chat history), launcher config, worker folder.
  `migrate-verify` ok except a few repos (see below). Claude logins were NOT copied (two servers refreshing one login sign each other out).
- Jonathan's phone is authorized (forced-command key) and the server is in the app.

## Still to do (in this order)
1. **Claude sign-in** on this server: main account and the cLaudePRO worker (Jonathan does it in the app; status says `logged_in: true`
   but `migrate-verify` listed both as pending, so check with `claude auth status`). Then Sessions > Restart all.
2. **MCP servers**: web ones need sign-in again (Figma, Mobbin, Cloudflare, Supabase, Stripe, Context7, Higgsfield): use the private-tmux `claude /mcp`
   method from `CLAUDE.md` / memory (wide pane, give Jonathan the link, type his pasted localhost URL into the pane). Still failing to connect:
   `plugin:playwright` (needs a browser install: `npx playwright install chromium` + OS libs), `plugin:claude-mem`, `plugin:antislop-contrast`
   (both launch `node`; find why, likely plugin cache path or hook setup), `aws-mcp` (needs AWS credentials via the app's key screen).
3. **Repos**: 2 broken git worktrees (`codeshift-keyboard-fix`, `yiimp-next`) were already broken on the old server (stale path): nothing to move.
   `testapirepo` was copied by hand (its GitHub repo is gone). Re-run `migrate-verify manifest.json` after step 1 to see what is left.
4. **Docker data stays on the old server** until Jonathan decides: volumes `backend_pgdata`, `osbtc-yiimp_db`, `backend_caddy_data` look like live
   databases. Ask what they run before moving (dump + restore, never copy files of a running database).
5. **Emulator farm**: per-instance ports (`emulator@<n>` with `-port` computed from n), several AVDs (phone, small phone, tablet, fold), a small
   `emu-shot` / `emu-record` helper so any session can say "boot AVD, install APK, screenshot, record 20 s, stop", plus an install of Compose/Espresso
   test runs. Teach it to sessions via a short skill or a note in `CLAUDE.md`.
6. **Backups**: nightly `restic` of `~/.claude`, `~/.config/claude-launcher`, `~/projects` to cloud storage (ask Jonathan which; costs money), and
   `claude-backup export` on a schedule is already supported by the app.
7. **Cluster workers**: with 12 cores and 92 GB, raise parallel tasks per chat (app: Cluster settings) and attach more accounts.

## Decisions waiting for Jonathan
- **CI runner**: this repo is public, so a self-hosted runner could run a stranger's pull request on this server. Proposal: runner only for the
  owner's own branches/PRs, fork PRs require approval (repo setting "Require approval for all outside collaborators"), release builds (they use the
  signing secrets) stay on GitHub's own runners, runner runs as its own unprivileged user inside a container (ephemeral), never as the Claude user.
  Needs his OK (changes who/what can run code, repo settings, workflow `runs-on`).
- When to switch over and sign the old server out (`migrate-signout-old`: deletes its Claude logins, GitHub token, autostart).
- Ubuntu 26.04 vs 24.04: stayed on 26.04, everything installed fine.

## Rules that apply here (from the master CLAUDE.md, repeated because they matter)
Ask before deleting data, touching production, changing DNS/mail, spending money or changing access. Never print or commit a key, token or password.
Never leave an emulator running. Server-script PRs install themselves on green main (`~/claude-setup.sh --api self-update <sha>`): do this on BOTH
servers while both exist.

## Handy checks
```
~/claude-setup.sh --api status | jq .            # script API, login state
systemctl show user-1000.slice -p MemoryMin      # the 40 GB reservation (uid of the main user)
free -h; systemctl status emulator@pixel35
sudo ufw status; sudo sshd -T | grep -E 'permitroot|passwordauth'
```
