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
- Attach is a guardrail, not a sandbox: sessions run as the same Unix user as the launcher. Say so in docs; do not claim isolation.
- Public repo: no personal values (addresses, users, key material) in code, tests or docs.

## Storage (`$LAUNCHER_CONFIG_DIR`)
- `hosts/<name>/meta.json` `{address, port, user, fingerprint, auth:"key"|"password", added}`; `hosts/<name>/key` (600); `hosts/<name>/known_hosts` (600). `hosts/` is 700.
- Attach state per project: `attach/<project-slug>.hosts.json` = `{"<host>": {}}` (a separate file from the workers' attach file,
  which clauderc-team iterates by key). Reuse the slug logic of `attach_session`/`claude_proj_slug`.

## Validation
- name: `^[a-z][a-z0-9-]{0,29}$` (lower case, shown in the app and used in tools).
- address: hostname `^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$` or IPv4/IPv6 literal; port 1-65535; user `^[a-z_][a-z0-9_-]{0,31}$`.
- fingerprint: `^SHA256:[A-Za-z0-9+/]{43}$`.

## Server API (claude-setup.sh `--api`, allowlisted in claude-launcher-api; SCRIPT_API 39)
All replies are the usual `{ok, data | error}`. Secrets come on stdin one per line (`read_secret_line`), never in argv.
- `host-list` → `{hosts:[{name,address,port,user,auth,fingerprint,added}]}` (no key material).
- `host-probe` stdin: address, port → `{fingerprint, keytype}` via `ssh-keyscan` + `ssh-keygen -lf`, no login. Error if unreachable.
- `host-add <name>` stdin: address, port, user, fingerprint, auth (`key`|`password`), secret.
  - `key`: secret is the private key, base64 (one line, no newlines). Reject unless `ssh-keygen -y` can read it; passphrase-protected keys are refused with a clear message.
  - `password`: generate `ed25519` key, install the public key (use `SSH_ASKPASS` with a 600 temp script that prints the password and `SSH_ASKPASS_REQUIRE=force`, or `sshpass -e` if installed; the password must not appear in argv or logs), then verify key login. Discard the password; set `auth:"password"` only to mean "key was installed with a password".
  - Both: pin the host key from probe (must match fingerprint), verify `ssh <host> true` works, then write the files. Nothing is saved if verification fails. Replace if the name exists.
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
- Add host dialog: name, address, port (22), user → **Check** (host-probe) shows the fingerprint to confirm → pick Key (paste, or upload a file; base64 on the wire) or Password → Save (host-add). Error text from the server is shown in the dialog (not only in a snackbar).
- Chat 👥 sheet: a "Servers" section with a switch per host (host-session / host-attach).
- Needs SCRIPT_API 39: raise `Updates.MIN_SCRIPT_API`.
