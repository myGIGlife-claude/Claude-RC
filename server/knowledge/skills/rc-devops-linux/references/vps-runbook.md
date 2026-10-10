# Single-VPS runbook: hardening checklist and safe changes (as of 2026-10)

## Safe-change procedure (every production change)
1. **Snapshot first**: provider snapshot of the VM (or at least a fresh restic/DB backup) and note its ID.
2. **Know the way back in**: confirm the provider web/serial console or rescue mode works and you know the login. Keys only, so
   the console needs a local password for the admin user or rescue boot.
3. **One change at a time**: never combine an OS upgrade, a firewall change and a deploy.
4. **Validate before applying**: `sshd -t`, `nginx -t`, `caddy validate`, `nft -c -f`, `systemd-analyze verify`, `docker compose config -q`,
   `ansible-playbook --check --diff`, `tofu plan`.
5. **Apply with a dead-man switch** for anything that can cut access (firewall, SSH, network):
   `sudo systemd-run --on-active=5m /usr/sbin/ufw disable` (or restore the old nft file) before the change; it prints the
   transient unit name (`run-r....timer`); cancel it (`sudo systemctl stop 'run-r*.timer'`, quoted so the shell does not expand
   the glob) only after a NEW SSH session succeeds.
6. **Keep the current SSH session open** until a new session from a second terminal works.
7. **Verify** from outside: the site loads over HTTPS, health endpoint OK, `systemctl --failed` empty, logs clean, external port scan as expected.
8. **Rollback path written before you start**: the exact commands (previous image digest, `git revert` + redeploy, snapshot restore).
9. Record what changed and why (commit in the infra repo, or a changelog file in /etc via etckeeper).

## Never lock yourself out of SSH
- `ufw allow OpenSSH` (or your port) BEFORE `ufw enable`; on port change: open the new port, add it next to the old one, test, then remove the old one.
- Ubuntu 24.04+: SSH is socket-activated; after changing `Port`/`ListenAddress` run `systemctl daemon-reload && systemctl restart ssh.socket`.
- Disabling passwords: confirm key login works for the admin user and `sudo` works (with its password or key-based rule) first.
- `AllowGroups`/`AllowUsers`: make sure your user is in it (`id`).
- Don't `iptables -F` with a DROP policy; don't flush nftables on a Docker host.
- fail2ban/CrowdSec: allow-list your management IP or VPN range so a typo-storm doesn't ban you.

## Hardening checklist (fresh VPS hosting sites and apps)
**Access**
- [ ] Supported LTS (Ubuntu 24.04/26.04 or Debian 13); `apt full-upgrade` and reboot
- [ ] Admin user with sudo, ed25519/FIDO key; root login off; password auth off (`sshd -T` confirms)
- [ ] `00-hardening.conf` drop-in; `AllowGroups`; key-only `AuthenticationMethods publickey`
- [ ] Provider console access tested; MFA on the provider and registrar accounts

**Network**
- [ ] Firewall default-deny: 22, 80, 443/tcp, 443/udp only; provider firewall mirrors it
- [ ] Docker ports bound to 127.0.0.1 or not published; external `nmap -Pn` confirms
- [ ] DB/Redis/admin UIs only on localhost/private network/WireGuard
- [ ] fail2ban or CrowdSec on sshd (and proxy logs if useful)

**System**
- [ ] unattended-upgrades on (security pocket), reboot policy, needrestart
- [ ] chrony/timesyncd synced, UTC
- [ ] Swapfile or zram; journald size cap; Docker log rotation; logrotate for app logs
- [ ] Every service: systemd unit or Compose, non-root, restart policy, healthcheck, memory limit

**Web**
- [ ] Caddy/Nginx terminates TLS (ACME automatic, ARI), HTTP->HTTPS redirect, HSTS when stable
- [ ] Security headers in one place; body size and timeouts set; rate limits on login/API
- [ ] CAA records; DNS TTLs sane; IPv6 served or explicitly absent
- [ ] Behind Cloudflare: Full (strict), origin limited to Cloudflare IPs or Tunnel, real-IP restored

**Data**
- [ ] restic/borg to off-site, append-only/immutable, separate credentials; DB dumps before file backup
- [ ] Backup failure alert + dead-man ping; monthly restore test recorded; RPO/RTO written

**Observability**
- [ ] External uptime + TLS expiry checks (from outside the VPS)
- [ ] Disk (space and inodes), memory/OOM, load, failed units alerts to phone/email
- [ ] Logs structured and retained N days; auth/sudo logs kept

**Process**
- [ ] Infra as code in git (cloud-init, Ansible or compose + scripts); secrets via SOPS/age
- [ ] CI deploys a pinned digest with a restricted deploy key; rollback rehearsed
- [ ] Runbook (restore, rollback, rotate, rebuild) stored off the server; game day each quarter

## DR runbook skeleton
```
Service: <name>      RPO: <e.g. 24 h>   RTO: <e.g. 4 h>   Owner: <role>
Backups: where, schedule, retention, how to get the repo password (not on this host)
Rebuild: 1) new VM from LTS image + cloud-init  2) run Ansible/compose  3) restore data (commands)
         4) point DNS (TTL already 300)  5) verify checklist  6) announce
Rollback of a bad deploy: compose with previous digest / blue-green switch back (commands)
Last restore test: <date>, took <minutes>, result
```

## Operating-system upgrade (LTS to LTS)
- Snapshot; read the release notes' "known issues"; upgrade one LTS hop at a time (`do-release-upgrade` on Ubuntu, sources change +
  `apt full-upgrade` on Debian per the release notes); keep a second SSH session (the upgrader starts a fallback sshd on another port:
  allow it in the firewall if prompted); re-run your verification checklist; check third-party apt repos for the new codename.
- Often cheaper: build a fresh VM on the new release from your IaC, restore data, switch DNS. That also tests your DR path.
