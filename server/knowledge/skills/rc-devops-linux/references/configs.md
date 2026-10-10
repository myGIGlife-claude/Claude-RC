# Copy-ready configs (as of 2026-10)

Minimal, own examples. Replace `app`, `example.com`, ports and paths. Test each with its checker before reloading.

## systemd service with sandboxing
```ini
# /etc/systemd/system/app.service   (check: systemd-analyze security app)
[Unit]
Description=app
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=300
StartLimitBurst=5

[Service]
# Type=notify if the app supports sd_notify. No trailing comments after values: systemd reads them as part of the value.
Type=simple
User=app
Group=app
WorkingDirectory=/opt/app
ExecStart=/opt/app/bin/app --listen 127.0.0.1:8080
Restart=on-failure
RestartSec=5
# The app reads it at $CREDENTIALS_DIRECTORY/db_password
LoadCredential=db_password:/etc/app/secrets/db_password
NoNewPrivileges=yes
ProtectSystem=strict
ReadWritePaths=/var/lib/app
StateDirectory=app
ProtectHome=yes
PrivateTmp=yes
PrivateDevices=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectControlGroups=yes
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
RestrictNamespaces=yes
LockPersonality=yes
# Remove MemoryDenyWriteExecute for JIT runtimes (Node, Java, .NET)
MemoryDenyWriteExecute=yes
SystemCallFilter=@system-service
CapabilityBoundingSet=
MemoryMax=512M

[Install]
WantedBy=multi-user.target
```

## systemd timer (replaces a cron line)
```ini
# /etc/systemd/system/backup.timer        (backup.service is Type=oneshot)
[Timer]
OnCalendar=*-*-* 03:00
RandomizedDelaySec=15m
Persistent=true
[Install]
WantedBy=timers.target
```
Add `OnFailure=notify@%n.service` in `backup.service` `[Unit]` to alert.

## nftables (host without Docker, or with Docker on 127.0.0.1-only ports)
```
# /etc/nftables.conf   check: nft -c -f /etc/nftables.conf
flush ruleset
table inet filter {
  chain input {
    type filter hook input priority 0; policy drop;
    ct state established,related accept
    ct state invalid drop
    iif lo accept
    meta l4proto { icmp, ipv6-icmp } accept
    tcp dport { 22, 80, 443 } accept
    udp dport 443 accept            # HTTP/3
  }
  chain forward { type filter hook forward priority 0; policy drop; }
}
```
With Docker on the host do NOT `flush ruleset` (it wipes Docker's tables): use `table inet filter` without flush, or ufw, and filter
container traffic in the `DOCKER-USER` chain.

## Caddyfile (automatic HTTPS, HTTP/3 on by default)
```
example.com {
	encode zstd gzip
	reverse_proxy 127.0.0.1:8080 {
		health_uri /readyz
	}
	header -Server
	request_body {
		max_size 10MB
	}
	log {
		output file /var/log/caddy/access.log
		format json
	}
}
www.example.com {
	redir https://example.com{uri} permanent
}
```
Apply: `caddy validate --config /etc/caddy/Caddyfile && systemctl reload caddy`.

## Nginx server block (1.25.1+ syntax)
```nginx
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
server {
    listen 443 ssl; listen [::]:443 ssl;
    listen 443 quic reuseport; listen [::]:443 quic reuseport;   # reuseport once per port
    http2 on; http3 on;
    server_name example.com;
    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Alt-Svc 'h3=":443"; ma=86400' always;
    client_max_body_size 10m;
    location /login { limit_req zone=login burst=5 nodelay; proxy_pass http://127.0.0.1:8080; include proxy_params; }
    location / { proxy_pass http://127.0.0.1:8080; include proxy_params; }
}
```
`proxy_params` should set `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`. `add_header` inside a `location` drops all
parent-level `add_header`s: repeat them or set headers in one place.

## compose.yaml (production shape)
```yaml
services:
  app:
    image: ghcr.io/org/app:1.4.2@sha256:<digest>
    user: "10001:10001"
    read_only: true
    tmpfs: [/tmp]
    cap_drop: [ALL]
    security_opt: ["no-new-privileges:true"]
    environment: { DB_HOST: db }
    secrets: [db_password]           # mounted at /run/secrets/db_password
    healthcheck: { test: ["CMD", "/app/healthcheck"], interval: 10s, timeout: 3s, retries: 3, start_period: 20s }
    deploy: { resources: { limits: { memory: 512M, cpus: "1.0", pids: 256 } } }
    depends_on: { db: { condition: service_healthy } }
    ports: ["127.0.0.1:8080:8080"]   # proxy on the host; never "8080:8080"
    networks: [backend]
    restart: unless-stopped
    logging: { driver: local, options: { max-size: 10m, max-file: "3" } }
  db:
    image: postgres:18.6@sha256:<digest>
    environment: { POSTGRES_PASSWORD_FILE: /run/secrets/db_password }
    secrets: [db_password]
    volumes: [dbdata:/var/lib/postgresql]   # PG 18 images use a versioned subdir under this path (unverified layout detail)
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U postgres"], interval: 5s, retries: 10 }
    networks: [backend]
    restart: unless-stopped
networks:
  backend: { internal: false }   # set internal: true if the app needs no outbound internet
volumes: { dbdata: {} }
secrets:
  db_password: { file: ./secrets/db_password }   # SOPS-decrypted at deploy, mode 600, git-ignored
```
Deploy: `docker compose pull && docker compose up -d --wait`.

## cloud-init (first boot)
```yaml
#cloud-config
users:
  - name: admin
    groups: [sudo, sshusers]
    shell: /bin/bash
    sudo: "ALL=(ALL) ALL"
    ssh_authorized_keys: ["ssh-ed25519 AAAA... admin"]
disable_root: true
ssh_pwauth: false
package_update: true
packages: [unattended-upgrades, chrony, ufw, restic]
runcmd:
  - [ufw, allow, OpenSSH]
  - [ufw, allow, "80,443/tcp"]
  - [ufw, --force, enable]
```
`groupadd sshusers` must exist before `AllowGroups sshusers` is enforced (add a `groups:` entry at top level).

## SOPS + age
```yaml
# .sops.yaml
creation_rules:
  - path_regex: secrets/prod/.*
    age: age1...prodpublickey
```
`sops -e -i secrets/prod/app.env` to encrypt; on the host `SOPS_AGE_KEY_FILE=/etc/app/age.key sops -d ...` at deploy.

## restic to S3-compatible storage
```bash
export RESTIC_REPOSITORY=s3:https://s3.example-provider.com/bucket/host1
export RESTIC_PASSWORD_FILE=/etc/restic/password     # 600, root
restic backup /etc /srv /var/backups/db --exclude-caches
restic forget --keep-daily 7 --keep-weekly 4 --keep-monthly 12 --prune
restic check --read-data-subset=5%
```
Credentials in an `EnvironmentFile=` with mode 600; the bucket key cannot delete objects (object lock / no-delete policy).
