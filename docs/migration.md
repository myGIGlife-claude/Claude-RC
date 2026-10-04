# Moving cLaudeRC to a new server

Do this when you change VPS. Replace `OLD` and `NEW` with your hosts and `you` with the
Linux user Claude runs as.

## The quick way: `claude-backup` (one encrypted file)

`claude-backup` ships with the server scripts (`~/.local/bin/claude-backup`, installed and updated by
`install.sh` and the app's *Update now*). It packs everything the sections below copy by hand into ONE
encrypted file and unpacks it on the new server.

1. **On OLD**, stop the Claude sessions first (in the app: stop them, or `tmux kill-server`): sign-in
   tokens rotate, and if OLD and NEW both refresh the same login, one of them is signed out. Then run
   (or ask Claude to run it):

   ```bash
   claude-backup export
   ```

   It writes `~/backups/clauderc-backup-<host>-<time>.tar.gz.gpg` and prints a passphrase like
   `K7QD-M2XW-...`. **Write the passphrase down: it is shown once and saved nowhere.** The summary also
   lists which git repos had work that exists only on OLD (unpushed commits, uncommitted changes, no
   remote; they are stored inside the file), anything too big to store (over 500 MB), and what is not
   covered. Options:

   - `--slim` leaves out plugin caches and marketplaces (Claude downloads them again): much smaller.
   - `--projects` stores ALL project folders, also clean repos and folders that are not git repos
     (without `node_modules`, `build`, ...), for when you would rather not reclone.
   - `--docker` also stores every named Docker volume (stop databases first for a consistent copy).
   - `--pass-file FILE` uses your own passphrase from a file instead of a generated one.

2. **On NEW**, install cLaudeRC with the phone key as in step 2 below (the `curl ... install.sh | bash -s -- 'ssh-ed25519 ...'`
   command from the app). NEW needs a way to log in to OLD with a key (a temporary one is fine). Then:

   ```bash
   claude-backup import backups/clauderc-backup-<host>-<time>.tar.gz.gpg --from you@OLD --clone
   ```

   It asks for the passphrase (typed, not echoed) and checks the whole file before writing anything, so a
   wrong passphrase or a damaged file changes nothing. Every existing file it is about to overwrite is
   first saved to `~/backups/pre-restore-<time>/`. It restores logins, keys, settings and chat history with
   the right permissions, puts the stored repos back into their project folders, and with `--clone` clones
   every clean repo again from its remote. Run it with `--dry-run` first to see what would be restored
   (`claude-backup list FILE` prints just the manifest). Docker volumes in the file go into new volumes;
   a volume that already holds data is never overwritten.

3. Follow the "Still to do" list it prints: recreate Docker containers, bring back services and VPN
   (Tailscale, databases, nodes: `claude-backup` never runs `sudo` or touches systemd), run
   `claude-autostart install` and restart the sessions, and sign the workers in again from the app
   (Claude tab › Accounts › *Re-sign in*). Then continue at step 5 (point the app at NEW) and 6 (check).

The file contains **every secret** of the server (SSH keys, tokens, Claude and worker logins), protected only
by the passphrase. Keep it out of shared places and **delete it on both servers once the move works**
(`rm ~/backups/clauderc-backup-*`, and the `pre-restore-*` folder on NEW).

The rest of this page is the manual way with `rsync`, a fallback if `claude-backup` is not an option.

---

# Manual move with rsync

Everything is copied with `rsync` over SSH, so the new server needs a way to log in to the old one
(a temporary key is fine).

## 0. Before you start

- **Save work that only exists on the old server.** For every project folder run
  `git status` and `git log @{u}..`: push unpushed commits, commit or copy what is dirty,
  and remember folders that are not git repositories.
- **Stop what uses a login while you copy it.** In the app: Sessions › *Restart all* is not enough,
  stop the sessions (or `tmux kill-server` on the old server). Claude's sign-in tokens rotate:
  if the old and the new server both refresh the same login, one is signed out. Finish the move,
  then only use the new server.
- Note anything that listens on a port or runs as a service (Docker containers and their volumes,
  databases, blockchain nodes, VPN such as Tailscale, emulators): they are not copied by the steps below.

## 1. Prepare NEW

```bash
sudo apt update && sudo apt install -y jq tmux git curl rsync util-linux   # util-linux provides flock
# Node (for `npx skills`, Claude plugins), Claude Code, gh, aws: install as you did on OLD
claude --version && gh --version
```

## 2. Install the cLaudeRC server scripts and authorize the phone

On NEW, as `you`, using the command the app shows (Settings › Connection › *Copy command*):

```bash
curl -fsSL https://raw.githubusercontent.com/myGIGlife-claude/Claude-RC/main/server/install.sh | bash -s -- 'ssh-ed25519 AAAA… clauderc'
```

The phone's key is the same for every server. The command also installs the autostart service
(sessions come back after a reboot) and prints the new server's host key fingerprints.

## 3. Copy the data (run on NEW, with OLD stopped as in step 0)

```bash
rsync -aH --info=progress2 you@OLD:~/.claude/            ~/.claude/              # settings, plugins, skills, memory, chat history
rsync -aH --info=progress2 you@OLD:~/.config/claude-launcher/ ~/.config/claude-launcher/   # keys, workers' logins, push config, chat PIN
rsync -aH --info=progress2 you@OLD:~/.agents/            ~/.agents/              # `npx skills` lock file and skills
rsync -aH --info=progress2 you@OLD:~/.config/gh/         ~/.config/gh/           # GitHub login
# only if you use them: ~/.aws ~/.config/glab-cli ~/.config/stripe ~/.config/b2 ~/.gitconfig ~/.docker
rsync -aH --info=progress2 you@OLD:~/projects/           ~/projects/             # your project folders
chmod 700 ~/.config/claude-launcher ~/.claude
```

Skip big caches you can rebuild (`~/.gradle`, `~/.cache`, `~/.npm`, `~/Android`). Paths inside
copied files that mention the old home directory keep working only if the user name is the same.

## 4. Bring the services back

- **Claude sessions:** `claude-autostart restore` (or reboot). It resumes each project's last conversation.
- **Server scripts:** the app's *Update now* (or re-run the install command) so the helpers match the app.
- **Environment for sessions:** `~/.config/claude-launcher/env` is mirrored into `~/.claude/settings.json`
  automatically; restart sessions once.
- **Docker, databases, nodes, VPN:** recreate the containers and restore their volumes
  (`docker run`/compose files live in the projects), then sign Tailscale in again.
- **Workers (cLaudeCluster):** in the app, Claude tab › Accounts › *Re-sign in* for each worker
  (copied logins usually work, a fresh sign-in is the safe choice). Recreate each project's
  `.cluster-allowed-tools` (it is not committed) and re-attach workers per chat with the 👥 button.
- **Push alerts:** nothing to do, the Firebase key and token files were copied; send a test push.

## 5. Point the app at NEW

Settings › Connection › add a server (host, port, username), compare the fingerprint with the one the
installer printed, tap *Trust*. When it works, remove OLD from the app and revoke the phone key there
(`~/bin/install-launcher-key.sh --remove '<key>'`).

## 6. Check

```bash
claude auth status                        # signed in
~/claude-setup.sh --api status | jq        # script_api, logins (the installer prints where it put the script)
gh auth status
```

In the app: Sessions list, open a chat, Claude tab (usage bars, plugins), Connections (all green),
Settings › Send a test push. Keep OLD for a few days before deleting it.
