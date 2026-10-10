---
name: rc-devops-linux
description: Running software in production on Linux servers and containers - Ubuntu/Debian admin, systemd units and timers, SSH hardening, ufw/nftables (Docker bypasses UFW), backups (restic/borg), monitoring, Caddy/Nginx/Apache, DNS/ACME, Docker Compose, Podman, Terraform/OpenTofu, Ansible, cloud-init, deploys and rollbacks, secrets (SOPS), DR. Use for a VPS, Dockerfile, compose.yaml, *.service, sshd_config, Caddyfile, nginx.conf, *.tf or deploy workflow.
---
# Linux servers, containers and operations (as of 2026-10)

> Facts here are dated (see Sources). Versions, deadlines and policies move: confirm the primary source before pinning a version or promising a date. Anything marked (unverified) is a lead, not a fact.

Scope: operating what you ship. App-level Docker/K8s/CI basics are in **rc-web-backend** (Dockerfile template, probes, expand/contract
migrations, PITR); security headers, TLS lifetimes, supply chain and Actions pinning in **rc-web-security-perf**. This skill goes
deeper on the server and the operations around it. Copy-ready configs: `references/configs.md`. Single-VPS checklist and
safe-change procedure: `references/vps-runbook.md`.

## Currency check

| Thing | Current (verified 2026-10-09) | Notes |
|---|---|---|
| Ubuntu LTS | **26.04** (2026-04, standard support to 2031-05), **24.04** (to 2029-05), **22.04** (to **2027-05**) | Ubuntu Pro ESM adds 5 years (26.04 to 2036). Interim releases get only 9 months. |
| Debian | **13 trixie** (2025-08-09, point 13.7 2026-09-12, LTS to 2030-06-30); **12 bookworm** oldstable (LTS to 2028-06-30); 11 bullseye LTS ended **2026-08-31** | trixie ships OpenSSH 10.0p1, systemd 257. |
| Ubuntu 26.04 server changes | **chrony** (with NTS, encrypted/authenticated time) replaces systemd-timesyncd on new installs; **sudo-rs** is the default `sudo` (classic sudo still installed as `sudo.ws`); OpenSSH 10.2p1; kernel 7.0 | Scripts that parse `sudo` output or rely on obscure sudoers features: test on 26.04. |
| OpenSSH | **10.6** (2026-10-06) | 10.0: DSA removed, pre-auth split into `sshd-auth`, `mlkem768x25519-sha256` (post-quantum hybrid) default kex, finite-field DH off in the server. 10.1: client warns on non-PQ kex (`WarnWeakCrypto`). 9.8+: `PerSourcePenalties` built-in. |
| systemd | 262 (2026-09-22) upstream; Ubuntu 26.04 ships 259 | **260 (2026-03-17) removed SysV init script support** (sysv-generator, rc-local). Distros on <=259 still run them, but write native units. |
| Nginx | stable **1.30.5**, mainline 1.31.6 | HTTP/3 via `listen 443 quic` (since 1.25); `http2 on;` replaces `listen ... http2` (1.25.1). |
| Caddy | **2.11.7** (2026-10-03) | 2.11.6 (2026-10-01) added 1-minute default idle read/write timeouts (slowloris) - long-poll/SSE/websocket backends may need tuning - and fixed CVE-2026-52844 plus several path-canonicalization/header fixes; 2.11.4 and 2.11.5 also carried security fixes: stay patched. |
| Apache httpd | **2.4.69** (2026-10-01) | HTTP/2 via mod_http2; no native HTTP/3 (put a CDN or Caddy/Nginx in front if needed) (unverified for 2026). |
| Docker Engine | **29.9.0** (2026-10-08); 29.0 (2025-11-10) | 29.0: containerd image store default on fresh installs, min API 1.44, Docker Content Trust removed from CLI, cgroup v1 deprecated (supported to 2029-05). nftables firewall backend is **experimental**. |
| Docker Compose | **v5.6.0** (2026-10-02) | v5.x: manual jobs, init containers (5.3), digest reconciliation honours `pull_policy` refresh windows (5.5). File is still the Compose Specification: no `version:` key. |
| Podman | **6.1.3** (2026-09-29), 5.8.x maintained | Quadlet (`.container` units under systemd) is the way to run it in production. |
| Kubernetes | see rc-web-backend (1.35-1.37 maintained; ingress-nginx retired 2026-03 -> Gateway API) | |
| Terraform | **1.16.5** (2026-10-02), **BUSL 1.1** (not open source) | IBM-owned HashiCorp. |
| OpenTofu | **1.13.1** (2026-10-01), MPL-2.0, Linux Foundation | Native state encryption (1.7+) with key providers (PBKDF2, AWS/GCP/Azure KMS, OpenBao transit, external). Drop-in for most Terraform <=1.5-era code. |
| Ansible | ansible-core **2.21.5** (2026-10-05), control node Python >= 3.12 | |
| Prometheus | **3.15.0** (2026-09-24); LTS line **3.13.x** | 3.x: UTF-8 metric names, native histograms, OTLP ingest. |
| Grafana | **13.2.3** (2026-09-29) | Security patches are frequent: auto-update or pin + Renovate. |
| Uptime Kuma | **2.5.6** (2026-10) | 2.x supports MariaDB/embedded DB; 1.x is legacy. |
| restic / borg | restic **0.19.1** (2026-07-05); borg **1.4.5** stable (2026-07-18), **borg 2.0 still beta** (b25) | Do not put production backups on borg 2 betas. |
| SOPS / OpenBao | SOPS **3.13.3** (2026-07-23); OpenBao **2.7.1** (2026-10-01) | OpenBao = MPL fork of Vault; Vault itself is BUSL. |
| Certificates | Max public TLS lifetime **200 days since 2026-03-15**, 100 days 2027-03-15, 47 days 2029-03-15; Let's Encrypt default -> 64 days 2027-02-10, 45 days 2028-02-16; `tlsserver` profile issues 45-day certs now | Details in rc-web-security-perf. Manual renewal is no longer viable. DNS-PERSIST-01 (static DNS record, no per-renewal DNS edit): Let's Encrypt staging only, production slipped past its Q2 2026 target (2026-10); other CAs offering it in production: unverified. |

### Older versions (what differs on legacy hosts)
- **Ubuntu 22.04**: standard support ends 2027-05: plan the 24.04/26.04 move now; `do-release-upgrade` only one LTS hop at a time (22.04 -> 24.04 -> 26.04). systemd-timesyncd, classic sudo, OpenSSH 8.9 (no PQ kex by default; DSA still compiled in).
- **Ubuntu 22.10 - 24.04**: sshd is **socket-activated** (`ssh.socket`). On 24.04+ a generator reads `Port`/`ListenAddress` from sshd_config, then `systemctl daemon-reload && systemctl restart ssh.socket`. On 22.04 (unless changed) and Debian, plain `ssh.service`.
- **Debian 12**: OpenSSH 9.2, systemd 252; `/etc/ssh/sshd_config.d/` exists. Debian 11 is out of LTS since 2026-08-31 (Freexian ELTS is paid).
- **Docker < 25** clients cannot talk to Engine 29 (API 1.44 minimum). Old hosts may still have `docker-compose` v1 (Python, EOL 2023): use the `docker compose` plugin.
- **Terraform <= 1.5.x** is the last MPL release; anything newer is BUSL. OpenTofu tracks 1.5 semantics and adds its own features.
- **CentOS 7/8, Ubuntu 18.04/20.04 without Pro**: no security updates. Treat as compromised-in-waiting; migrate, don't patch around.

## What changed / stop doing

| Stop (old) | Do instead (new) | Since |
|---|---|---|
| `PasswordAuthentication yes`, root login with password | Keys only (ed25519 or FIDO `sk-ed25519`), `PermitRootLogin no`, a sudo user; verify with `sshd -T` | long-standing; OpenSSH 10 removed DSA |
| Editing `/etc/ssh/sshd_config` and assuming it applies | Drop-in in `sshd_config.d/` named to sort **first** (`00-hardening.conf`): sshd uses the **first** value it reads; `50-cloud-init.conf` (or the image's `60-cloudimg-settings.conf`) may set `PasswordAuthentication yes` | Debian/Ubuntu Include layout |
| Raw `iptables` scripts, `iptables-persistent` | `nftables` (`/etc/nftables.conf`) or ufw/firewalld front-ends (they use nft backend) | Debian 10+/Ubuntu 20.04+ |
| "UFW blocks it, so publishing a Docker port is safe" | Docker's NAT rules bypass ufw's INPUT chain. Publish on `127.0.0.1:` only, or no `ports:` (proxy on the same network), or filter in `DOCKER-USER` | Docker docs, all versions |
| SysV `/etc/init.d` scripts, `rc.local`, `nohup app &`, screen/tmux in prod | systemd unit with hardening options, or a container with a restart policy | systemd 260 removed SysV support (2026-03) |
| cron for everything | systemd timers (`Persistent=true`, logs in journal, `RandomizedDelaySec`) ; cron is fine for simple user jobs | - |
| ntpd / ntpdate | chrony (default on 26.04, with NTS) or systemd-timesyncd | Ubuntu 26.04 |
| `docker-compose` v1, `version: "3.8"` in compose files | `docker compose` (v2+/v5 plugin), Compose Specification, no `version:` | Compose v1 EOL 2023 |
| `image: app:latest`, `postgres:16` floating tags | Pin a version tag **plus digest** (`@sha256:...`), Renovate/Dependabot bumps it | - |
| `docker run --privileged`, mounting `/var/run/docker.sock` into apps | Specific `cap_add`, devices; never expose the socket (it is root on the host). Use a socket proxy with an allow-list if a tool really needs it | - |
| Containers as root | `user:` non-root, `read_only: true`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`; rootless Docker or Podman where possible | - |
| Docker Content Trust (`DOCKER_CONTENT_TRUST=1`) | cosign/Sigstore signatures and attestations | removed from CLI in Docker 29 |
| Secrets in `.env` committed, in `environment:`, baked into images | Compose `secrets:` (files), SOPS+age in git, OpenBao/Vault or cloud secret manager; BuildKit `--mount=type=secret` at build | - |
| Terraform assumed open source | Terraform is BUSL since 1.6 (2023-08); OpenTofu is the MPL fork. Pick one per repo; state is portable from TF <=1.5/1.6 era | 2023-08 |
| Hand-configured "snowflake" servers | cloud-init for first boot + Ansible (or image builds) in git; rebuild beats repair | - |
| Backups that were never restored | Scheduled restore test into a scratch host, with a checksum/row-count check, recorded | - |
| Cron-based certbot with 90-day assumption | ACME client with ARI (Caddy, lego, cert-manager; check your certbot version supports ARI) and expiry alerting at 1/3 lifetime | lifetimes shrinking 2026-2029 |
| `ingress-nginx` on Kubernetes | Gateway API implementation | retired 2026-03 |

## Do this

### Pick the smallest platform that works (and when NOT to add things)
1. Static site: object storage/CDN or Caddy `file_server`. No containers needed.
2. One app + DB, low traffic: **one VPS**, Docker Compose (or systemd + native packages), Caddy in front, restic to off-site storage. Most projects stop here.
3. Several services, need HA: 2+ VMs behind a managed load balancer, managed DB. Still no Kubernetes.
4. Kubernetes only when you have many services, a team to run it, and need its scheduling/autoscaling: use a **managed** control plane (EKS, GKE Autopilot, AKS, DigitalOcean/Linode/Scaleway/OVH managed K8s); k3s for small self-hosted. Concepts to know: Deployment, Service, Gateway API, ConfigMap/Secret, requests/limits, probes, PodDisruptionBudget, NetworkPolicy.
- Don't add: a service mesh, Vault, a log cluster, or Terraform for a single VPS. A README plus cloud-init/Ansible is enough until there are 3+ hosts. Every tool you add is something to patch.

### Base server (Ubuntu/Debian)
- Install from the provider's current LTS image; set hostname, timezone UTC, locale. First-boot config via **cloud-init** (`#cloud-config`: users with `ssh_authorized_keys`, `disable_root: true`, `ssh_pwauth: false`, packages, `runcmd`).
- Admin user in `sudo` group with key login; `PermitRootLogin no`. Optional: FIDO2 keys (`ssh-keygen -t ed25519-sk`).
- SSH drop-in (sorts first; verify the effective value with `sudo sshd -T | grep -Ei 'passwordauth|permitroot|kbdinter'`):
  ```
  # /etc/ssh/sshd_config.d/00-hardening.conf
  PermitRootLogin no
  PasswordAuthentication no
  KbdInteractiveAuthentication no
  AuthenticationMethods publickey
  AllowGroups sshusers
  ```
  `sudo sshd -t` then `systemctl reload ssh`, **keep the current session open** and test a new login in a second terminal.
- Unattended security updates: `unattended-upgrades` (default on Ubuntu, `apt install unattended-upgrades` + `dpkg-reconfigure` on Debian). Decide on `Automatic-Reboot` with a window (`"03:30"`) or use `needrestart` + a reboot alert; Ubuntu Livepatch (Pro) for kernel fixes without reboot.
- Time: chrony (26.04 default) or timesyncd; check `chronyc tracking` / `timedatectl`. TLS, TOTP and logs break with clock skew.
- Swap: small swapfile (1-2 GB) or zram on small VPSes so the OOM killer is not your first signal; `vm.swappiness=10` for servers. Alert on OOM kills (`journalctl -k | grep -i oom`).
- Disk: alert at 80% **and on inodes** (`df -i`); journald `SystemMaxUse=1G` in `/etc/systemd/journald.conf.d/`; logrotate for app files (`copytruncate` only if the app cannot reopen); Docker logs: `"log-opts": {"max-size": "10m", "max-file": "3"}` in `/etc/docker/daemon.json` or `local` driver - default json-file grows forever.

### Firewall
- Default deny inbound; allow 22 (or your SSH port), 80, 443/tcp and **443/udp** if you serve HTTP/3. ufw: `ufw default deny incoming; ufw allow OpenSSH; ufw allow 80,443/tcp; ufw allow 443/udp; ufw enable` (allow SSH **before** enable).
- Or nftables directly (`references/configs.md`). Don't mix hand rules with ufw/firewalld.
- Docker: assume every `ports: "8080:8080"` is reachable from the internet regardless of ufw. Use `"127.0.0.1:8080:8080"` or only `expose` + reverse proxy on a shared network. Audit with `ss -tlnp` and an **external** scan (`nmap -Pn host`).
- Cloud security groups/provider firewall as a second layer; databases, Redis, admin panels never public. Admin access over WireGuard/Tailscale instead of open ports.
- Brute force: key-only SSH removes the risk; still use fail2ban (sshd jail with `backend = systemd`) or CrowdSec (community blocklists, bouncer for nftables/Caddy/Nginx). OpenSSH's built-in `PerSourcePenalties` (9.8+) already throttles.

### systemd services and timers
- Every long-running non-container app is a unit with a dedicated user, restart policy and sandboxing; check with `systemd-analyze security <unit>` (aim for < 5 "OK"). Template in `references/configs.md`.
- Key options: `DynamicUser=yes` or `User=`; `ProtectSystem=strict` + `ReadWritePaths=`; `ProtectHome=yes`; `PrivateTmp=yes`; `NoNewPrivileges=yes`; `CapabilityBoundingSet=` (empty) or `AmbientCapabilities=CAP_NET_BIND_SERVICE`; `RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX`; `SystemCallFilter=@system-service`; `MemoryMax=`; `Restart=on-failure`; `EnvironmentFile=` or better `LoadCredential=` for secrets.
- Edit vendor units with `systemctl edit <unit>` (drop-in), never in `/lib/systemd/system`. After file changes: `systemctl daemon-reload`.
- Timers: `OnCalendar=*-*-* 03:00`, `Persistent=true`, `RandomizedDelaySec=15m`; `systemctl list-timers`. A failed run shows in `systemctl --failed`; add `OnFailure=` to notify.
- Logs: `journalctl -u app -f`, `-p warning -b`, `--since "1 hour ago"`, `-o json`. Persistent journal (`Storage=persistent`).

### Reverse proxy / web server
- **Default: Caddy** for a VPS: automatic HTTPS (ACME, ARI, OCSP-less), HTTP/2+3 on, sane TLS; a Caddyfile is a few lines. Nginx when you need its ecosystem/perf tuning or already run it; Apache when the app needs `.htaccess`/mod_php (legacy PHP hosting).
- Terminate TLS at the proxy; apps listen on `127.0.0.1` or a Docker network. Forward `X-Forwarded-For/Proto` and configure the app's trusted-proxy list (otherwise rate limits and logs see only the proxy IP).
- TLS: 1.2 + 1.3 only, Mozilla "intermediate" profile (ssl-config.mozilla.org); HSTS once HTTPS works everywhere; no OCSP stapling config needed for Let's Encrypt (its OCSP responders went off on 2025-08-06).
- Rate-limit login/API paths at the proxy (Nginx `limit_req_zone`; Caddy needs the `rate_limit` plugin or do it in-app). Set body size limits (`client_max_body_size`, Caddy `request_body max_size`) and timeouts.
- Security headers: set them once, at the proxy or the app, not both (duplicates confuse browsers). Header values: rc-web-security-perf.
- Static files served by the proxy with long `Cache-Control: immutable` for hashed assets; HTML `no-cache`. Compression: zstd/br/gzip (Caddy `encode zstd gzip`).
- Zero-downtime reload: `caddy reload` / `nginx -t && systemctl reload nginx` / `apachectl configtest && systemctl reload apache2`. Never `restart` to apply config.

### DNS and certificates
- Records: A/AAAA (serve IPv6 too), CNAME for aliases (not at apex; use ALIAS/flattening), MX+SPF/DKIM/DMARC even for no-mail domains (`v=spf1 -all`, `p=reject`), CAA (`0 issue "letsencrypt.org"` + your backup CA, `iodef`).
- TTL strategy: 300 s on records you may move; lower to 60 s **a TTL-period before** a migration, raise back to 3600+ after. NS/registrar changes take up to 48 h.
- Registrar: MFA, registrar lock, auto-renew, a non-company-domain contact email. DNSSEC only if your DNS host automates key rollover (manual DNSSEC = self-inflicted outage).
- ACME: HTTP-01 needs port 80 open; DNS-01 for wildcards/internal hosts (scoped API token, only for the `_acme-challenge` zone if the DNS host allows). Monitor expiry externally (Uptime Kuma, Blackbox exporter).
- Cloudflare proxy (orange cloud): set SSL mode **Full (strict)** (never Flexible: it makes HTTP to origin and causes redirect loops), restore real client IP from `CF-Connecting-IP` only for Cloudflare ranges, firewall origin 80/443 to Cloudflare IPs (or use a Tunnel) so the origin cannot be hit directly. Proxy hides the IP only if old DNS history and mail records don't leak it.

### Docker and Compose in production
- One `compose.yaml` per stack in git; `.env` only for non-secret config; `docker compose config` to see the rendered result. Template in `references/configs.md`.
- Per service: pinned `image@sha256`, `restart: unless-stopped`, `healthcheck`, `user`, `read_only`, `tmpfs` for writable temp, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, `deploy.resources.limits` (memory/cpus/pids), named volumes for data, `depends_on: {db: {condition: service_healthy}}`, logging limits.
- Networks: an internal network for app<->db (`internal: true`), only the proxy on the published network.
- Build: BuildKit is the default builder; multi-stage, `--mount=type=cache` for package caches, `--mount=type=secret` for build secrets, `docker buildx build --sbom=true --provenance=mode=max`. Scan in CI (Trivy/Grype/Docker Scout) and fail on fixable critical/high.
- Rootless Docker or Podman for multi-tenant or untrusted workloads; otherwise at least userns-remap or non-root containers. Members of the `docker` group are root-equivalent.
- Updates: Renovate/Dependabot bump digests; deploy is `docker compose pull && docker compose up -d --wait` (waits for healthchecks). Avoid Watchtower-style auto-updates on stateful services (Watchtower is archived (unverified)).
- Podman: rootless by default, systemd-native via **Quadlet** (`~/.config/containers/systemd/app.container`), `podman auto-update` with `AutoUpdate=registry` and rollback on failed health.

### Infrastructure as Code and config management
- cloud-init: first boot only (users, keys, packages, hostname). Not for ongoing changes.
- Ansible: idempotent roles in git; `--check --diff` before apply; secrets via `ansible-vault` or SOPS; `ansible-lint` in CI. Run against staging first.
- Terraform/OpenTofu: remote state with locking (S3 native lockfile `use_lockfile = true` or a backend with locking), state encrypted (OpenTofu native encryption), `plan` in PR, `apply` only from CI on merge with the saved plan; pin providers in `.terraform.lock.hcl`; never edit state by hand (`state mv`/`import` blocks). Choose OpenTofu if licence matters (MPL) or you want state encryption; Terraform if you rely on HCP Terraform features.

### CI/CD and deploys
- Build once, tag by git SHA, push to a registry, deploy that exact digest everywhere. Hardening of GitHub Actions (SHA pinning, `permissions`, OIDC, `pull_request_target`): rc-web-security-perf. GitLab CI: protected branches/variables, `rules:` not `only/except`, OIDC `id_tokens` to clouds.
- Deploy from CI to a VPS with a dedicated deploy user and a **restricted key** (`command=` forced command or a narrow sudoers rule), not your admin key. Use GitHub/GitLab **environments** with required reviewers for production.
- Strategies: **rolling** (default; needs N>=2 or a short blip), **blue/green** (two stacks, flip the proxy upstream, instant rollback), **canary** (small % via weighted upstream or flag; needs metrics to judge). On one VPS: blue/green with two compose projects and a Caddy/Nginx upstream switch.
- Rollback path = previous image digest + backward-compatible DB (expand/contract migrations, rc-web-backend). Run migrations as a separate one-shot job before switching traffic; never auto-run destructive migrations on app start in multi-replica setups.
- Preview environments per PR: ephemeral, seeded with fake data, auto-destroyed on close, `noindex`, behind auth.

### Secrets
- SOPS + age for secrets in git (`.sops.yaml` with creation rules; one age key per environment, private key only on the host/CI). OpenBao/Vault when you need dynamic/short-lived DB credentials or many services; cloud secret managers on that cloud.
- Deliver as files (Compose `secrets:`, systemd `LoadCredential=`), not env vars where possible (env leaks into `docker inspect`, crash dumps, child processes).
- Rotate on staff change and after any leak; a leaked secret is rotated, not just deleted from git.

### Backups and disaster recovery
- 3-2-1: 3 copies, 2 media, 1 off-site **and** immutable/append-only (restic with object lock or `rest-server --append-only`; borg `--append-only` repo keys). Different account/credentials from the server, so a compromised host cannot delete backups.
- Back up data, not the OS: DB dumps or PITR (rc-web-backend), volumes, `/etc`, compose files, secrets (encrypted). Stop-or-snapshot for consistency: `pg_dump`/`mysqldump --single-transaction` before the file backup.
- restic: `restic backup`, `restic forget --keep-daily 7 --keep-weekly 4 --keep-monthly 12 --prune`, `restic check --read-data-subset=5%` weekly. Timer + `OnFailure=` alert + a dead-man's switch (healthchecks.io-style ping) so a silent stop is noticed.
- **Restore test** monthly (automated if possible): restore to a scratch VM, start the app, run a sanity query, record time taken = your real RTO.
- Write down RPO (how much data you may lose) and RTO (how long to recover); runbook with exact commands; store the runbook and the repo password **off the server**. Game day: once a quarter, rebuild from scratch using only the runbook.

### Monitoring, logs, alerting
- Minimum for one VPS: external uptime/TLS-expiry checks (Uptime Kuma self-hosted elsewhere, or a hosted checker), node metrics (Netdata or Prometheus node_exporter + Grafana), disk/inode/memory/OOM alerts, backup success ping.
- Bigger: Prometheus (or a compatible TSDB) + Alertmanager + Grafana; Loki or a hosted backend for logs; OpenTelemetry Collector to receive OTLP traces/metrics/logs from apps and forward (vendor-neutral). Ship logs with the Collector, Grafana Alloy, Vector or Fluent Bit; journald -> collector, not files.
- Structured JSON logs with request/trace IDs; no secrets/PII. Retention matched to need and privacy law.
- Alert on symptoms users feel (SLO burn, error rate, latency, cert expiry, backup missing), not on every CPU spike. Every alert must be actionable and link a runbook; delete alerts nobody acts on.

### Cost control
- Right-size after a month of metrics; set provider budget alerts; delete unattached volumes, old snapshots, idle IPs, stale preview environments. Egress and managed-NAT/load balancer fees are the usual surprise. Object storage lifecycle rules for old backups/logs.

## Security
- Attack surface: only 22/80/443 open, verified from outside. Everything else on localhost, a private network or WireGuard.
- SSH: keys only, no root, `AllowGroups`, no agent forwarding to untrusted hosts; consider SSH certificates (short-lived) for teams. Remove departed users' keys the same day.
- Least privilege: one Unix user per service; containers non-root with dropped caps; no docker socket in apps; sudo rules narrow (`NOPASSWD` only for specific commands).
- Patch cadence: unattended security updates + weekly review of `apt list --upgradable`, kernel reboots in a window, images rebuilt weekly even without code changes (base image CVEs).
- Supply chain for ops: install repos with signed-by keyrings (`/etc/apt/keyrings/*.asc`, `signed-by=`), never `curl | sudo bash` from unknown sources without reading it; pin image digests and verify signatures where offered.
- Secrets: never in images, compose files, shell history (`HISTCONTROL=ignorespace`), CI logs. File perms 600, owned by the service user.
- Logging for incidents: auth logs retained (journal), sudo logging, file-integrity check on critical paths (AIDE) for regulated setups.
- Kernel/sysctl basics: `net.ipv4.conf.all.rp_filter=1`, `kernel.kptr_restrict=2`, `fs.protected_*` (defaults on modern Ubuntu/Debian); don't paste giant sysctl "hardening" lists without understanding them.

## Performance & quality
- Targets for a small VPS: CPU steal < 5% (else the host is oversold), load < cores, memory headroom >= 20%, disk latency p99 < 10 ms, no OOM kills, 0 failed units (`systemctl --failed`).
- Tools: `htop`/`btop`, `iostat -x 1`, `vmstat 1`, `ss -s`, `journalctl -p err -b`, `systemd-analyze blame`, `docker stats`, `ncdu` for disk.
- Proxy: keepalive to upstreams, HTTP/2+3 to clients, compression, caching of static assets; measure p95 latency at the proxy log (`$upstream_response_time`).
- Availability: measure from outside; SLOs and burn-rate alerting per rc-web-backend.

## Testing & tooling
- Config tests before reload: `sshd -t`, `nginx -t`, `caddy validate --config /etc/caddy/Caddyfile`, `apachectl configtest`, `nft -c -f /etc/nftables.conf`, `systemd-analyze verify app.service`, `docker compose config -q`.
- Lint: `hadolint` (Dockerfile), `shellcheck` (scripts), `ansible-lint`, `tflint`/`tofu validate`/`terraform validate`, `trivy config` / `checkov` for IaC misconfig, `actionlint` + `zizmor` for workflows.
- Test infra changes on a throwaway VM or Multipass/LXD/Vagrant before production. Molecule for Ansible roles.
- Security audit of a host: `lynis audit system`, `systemd-analyze security`, external `nmap`, `testssl.sh` for TLS.

## Common mistakes in AI-written code
- Puts hardening in `/etc/ssh/sshd_config` while a `sshd_config.d/50-cloud-init.conf` with `PasswordAuthentication yes` wins; never checks `sshd -T`.
- Changes the SSH port in sshd_config on Ubuntu 24.04+ and forgets `daemon-reload` + `restart ssh.socket`, or opens the new port in the firewall after enabling it -> lockout.
- `ufw enable` before `ufw allow OpenSSH`.
- `ports: "5432:5432"` for Postgres/Redis "because ufw blocks it".
- `version: "3.8"` at the top of compose files; `docker-compose` (hyphen) commands; `links:`; `container_name` on scaled services.
- `restart: always` with no healthcheck; `depends_on` without `condition: service_healthy`; healthchecks using `curl` in an image that has no curl.
- `privileged: true` or docker.sock mounts to "fix" permission errors; `chmod 777` on volumes instead of matching UID/GID.
- Secrets in `environment:` or `ARG`; `.env` copied into the image.
- systemd units with `Type=forking` for apps that don't fork, `Restart=always` without `RestartSec`/`StartLimit*`, `User=root`, or edits in `/lib/systemd/system`.
- Nginx: `listen 443 ssl http2;` (deprecated form), `if` blocks for routing, `proxy_pass` with a trailing-slash mismatch, missing `proxy_set_header Host`/`X-Forwarded-Proto`, `add_header` in a nested block silently dropping the parent's headers.
- Cloudflare "Flexible" SSL; certbot `--standalone` while Nginx holds port 80; hard-coded 90-day renewal assumptions.
- `apt-key add` (deprecated for years; the binary is gone from apt 3 on Ubuntu 26.04 and Debian 13): use `/etc/apt/keyrings` + `signed-by`.
- Terraform: `terraform apply -auto-approve` from a laptop, local state in git, provider versions unpinned; mixing `terraform` and `tofu` on one state.
- Backup scripts with no failure alert, no `forget/prune`, no restore test, repo password stored next to the repo.
- `iptables -F` "to reset" on a host running Docker (wipes Docker's rules) or with a default DROP policy (instant lockout).

## Before you ship
- [ ] Supported OS release with security updates on; reboot policy set
- [ ] SSH keys only, root login off, verified with `sshd -T`; a second admin path exists (provider console/rescue)
- [ ] Firewall default-deny; external `nmap` shows only intended ports; no Docker port published on 0.0.0.0 by accident
- [ ] Every service under systemd or Compose with restart policy, healthcheck, resource limits, non-root user
- [ ] TLS automatic (ACME, ARI) with expiry monitoring; CAA set; HSTS once stable
- [ ] Secrets outside git/images (SOPS/secret manager), file perms 600
- [ ] Backups automated, off-site, immutable, alerting on failure; **restore tested** and RPO/RTO written down
- [ ] Monitoring: external uptime, disk/inode/memory, cert expiry, backup ping; alerts routed to a human
- [ ] Deploy is scripted from CI with a pinned digest; rollback tested (previous digest + compatible schema)
- [ ] Runbook (restore, rollback, rotate secrets, rebuild host) stored off the server
- [ ] Change made via the safe-change procedure (`references/vps-runbook.md`): snapshot, one change, verify, rollback ready

## Sources
- https://ubuntu.com/about/release-cycle - Ubuntu 22.04/24.04/26.04 support dates (2026-10-09)
- https://ubuntu.com/blog/canonical-releases-ubuntu-26-04-lts-resolute-raccoon and https://launchpad.net/ubuntu/+source/openssh - 26.04 released 2026-04-23, Linux 7.0, sudo-rs, OpenSSH 10.2p1; chrony with NTS pools, `sudo.ws`, no `apt-key`, `ssh.socket` confirmed on a 26.04.1 host (2026-10-09)
- https://discourse.ubuntu.com/t/sshd-now-uses-socket-based-activation-ubuntu-22-10-and-later/30189 - ssh.socket activation and port changes (2026-10-09)
- https://www.debian.org/releases/ - trixie/bookworm/bullseye dates and LTS ends (2026-10-09)
- https://www.debian.org/releases/trixie/release-notes/whats-new.en.html - OpenSSH 10.0p1, systemd 257 in trixie (2026-10-09)
- https://www.openssh.org/releasenotes.html - OpenSSH 10.6, DSA removal, sshd-auth, PQ kex default, WarnWeakCrypto (2026-10-09)
- https://raw.githubusercontent.com/systemd/systemd/main/NEWS and https://github.com/systemd/systemd/releases - v260 (2026-03-17) removed SysV scripts, v262 (2026-09-22) latest, 263 unreleased (2026-10-09)
- https://nginx.org/en/download.html - nginx 1.30.5 stable / 1.31.6 mainline (2026-10-09)
- https://github.com/caddyserver/caddy/releases - Caddy 2.11.7 (2026-10-03), timeouts and CVE-2026-52844 in 2.11.6 (2026-10-01) (2026-10-09)
- https://letsencrypt.org/2024/12/05/ending-ocsp - OCSP responders off 2025-08-06 (2026-10-09)
- https://httpd.apache.org/ - Apache 2.4.69 (2026-10-09)
- https://docs.docker.com/engine/release-notes/ - Docker 29.x changes (2026-10-09)
- https://docs.docker.com/engine/network/packet-filtering-firewalls/ - Docker bypasses ufw, nftables backend experimental (2026-10-09)
- https://github.com/docker/compose/releases - Compose v5.6.0 and 5.x features (2026-10-09)
- https://github.com/containers/podman/releases - Podman 6.1.3, Quadlet (2026-10-09)
- https://github.com/hashicorp/terraform/releases - Terraform 1.16.5, BUSL (2026-10-09)
- https://github.com/opentofu/opentofu/releases - OpenTofu 1.13.1, 1.12 key providers (2026-10-09)
- https://pypi.org/project/ansible-core/ - ansible-core 2.21.5, Python >= 3.12 (2026-10-09)
- https://github.com/prometheus/prometheus/releases - Prometheus 3.15.0, LTS 3.13 (2026-10-09)
- https://github.com/grafana/grafana/releases - Grafana 13.2.3 (2026-10-09)
- https://github.com/louislam/uptime-kuma/releases - Uptime Kuma 2.5.6 (2026-10-09)
- https://github.com/restic/restic/releases - restic 0.19.1 (2026-10-09)
- https://github.com/borgbackup/borg/releases - borg 1.4.5 stable, 2.0 beta (2026-10-09)
- https://github.com/getsops/sops/releases - SOPS 3.13.3 (2026-10-09)
- https://github.com/openbao/openbao/releases - OpenBao 2.7.1 (2026-10-09)
- https://letsencrypt.org/2025/12/02/from-90-to-45 - LE 64/45-day timeline, ARI advice (2026-10-09)
- https://letsencrypt.org/2026/02/18/dns-persist-01 and https://community.letsencrypt.org/t/dns-persist-01-deployment-status-and-timeline/246468 - DNS-PERSIST-01 status (2026-10-09)
- https://ssl-config.mozilla.org/ - TLS server profiles (2026-10-09)
