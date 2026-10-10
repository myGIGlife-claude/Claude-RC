# Service map and limits (as of 2026-10)

Names change often: check the console/docs before writing IaC. Rows marked (unverified) were not re-checked on a primary source on 2026-10-09.

## Service mapping across the six clouds

| Need | AWS | Azure | Google Cloud | Cloudflare | OVHcloud | IBM Cloud |
|---|---|---|---|---|---|---|
| VMs | EC2 | Virtual Machines | Compute Engine | - (Containers only) | VPS, Public Cloud Instances, Bare Metal | Virtual Servers for VPC, Bare Metal, Power Virtual Server |
| Containers (no K8s) | ECS (Fargate), App Runner (unverified status), Lambda container images | Container Apps, App Service | Cloud Run (services + jobs) | Containers (GA 2026-04, Workers Paid) | Managed Kubernetes or VPS + Docker (no serverless containers, unverified) | Code Engine |
| Kubernetes | EKS | AKS | GKE (Autopilot / Standard) | - | Managed Kubernetes Service (free control plane, unverified) | IKS, Red Hat OpenShift on IBM Cloud |
| Serverless functions | Lambda | Functions (Flex Consumption) | Cloud Run functions (ex Cloud Functions) | Workers | none first-party (unverified) | Code Engine functions |
| Object storage | S3 | Blob Storage | Cloud Storage | R2 | Object Storage (S3 API) | Cloud Object Storage |
| Managed SQL | RDS, Aurora (incl. DSQL) | Azure Database for PostgreSQL/MySQL flexible server, Azure SQL | Cloud SQL, AlloyDB, Spanner | D1 (SQLite), Hyperdrive (pool to your Postgres/MySQL) | Managed Databases (PostgreSQL, MySQL, ...) | Databases for PostgreSQL/MySQL, Db2 |
| NoSQL / KV | DynamoDB | Cosmos DB | Firestore, Bigtable | KV, Durable Objects | Managed Valkey/MongoDB (unverified) | Cloudant, Databases for MongoDB/Redis |
| Queues / events | SQS, SNS, EventBridge | Service Bus, Event Grid, Event Hubs | Pub/Sub, Cloud Tasks, Eventarc | Queues, Workflows | Managed Kafka (unverified) | Event Streams (Kafka), MQ |
| Workflows / durable | Step Functions, Lambda durable functions | Durable Functions, Logic Apps | Workflows | Workflows, Durable Objects | - | - |
| CDN | CloudFront | Front Door | Cloud CDN | (core product) | CDN (unverified) | CDN via partner (unverified) |
| DNS | Route 53 | Azure DNS | Cloud DNS | DNS (free) | DNS (with domains) | DNS Services, CIS (Cloudflare-based, unverified) |
| Identity for people | IAM Identity Center | Entra ID | Cloud Identity / Workforce Identity Federation | Zero Trust Access | OVHcloud IAM + SSO (SAML) | IAM + App ID, trusted profiles |
| Workload identity | IAM roles, Roles Anywhere, OIDC provider | Managed identity, workload identity federation | Service accounts (attached), Workload Identity Federation | API tokens (scoped), Access service tokens | IAM policies + API (OAuth2 service accounts, unverified) | Trusted profiles, service IDs |
| Secrets | Secrets Manager, SSM Parameter Store | Key Vault | Secret Manager | Workers secrets, Secrets Store | Secret Manager (unverified) / KMS | Secrets Manager, Key Protect, Hyper Protect Crypto Services |
| Observability | CloudWatch, X-Ray, CloudTrail | Azure Monitor, App Insights, Log Analytics | Cloud Logging/Monitoring/Trace, Audit Logs | Workers Logs, Logpush, Analytics | Logs Data Platform | Cloud Logs, Monitoring, Activity Tracker |
| Budget alerts | AWS Budgets, Cost Anomaly Detection | Cost Management budgets | Billing budgets (+ Pub/Sub for automation) | Billing notifications (usage-based) | Budget alerts in Control Panel (unverified) | Spending notifications |

## Serverless and edge limits (verified 2026-10-09 unless marked)

| Platform | Max run time | Memory / CPU | Payload / size | Notes |
|---|---|---|---|---|
| AWS Lambda | 15 min (90 min for Lambda Managed Instances async/event-source) | 128 MB-10,240 MB; 1 vCPU at 1,769 MB | 6 MB sync req/resp, 200 MB streamed, 1 MB async; zip 50 MB / 250 MB unzipped; image 10 GB | Default 1,000 concurrent per Region; new accounts lower, raised automatically. `/tmp` 512 MB-10 GB. Env vars 4 KB total. |
| Lambda durable functions | executions checkpoint/resume (long-running) | - | 100 MB persisted per execution, 3,000 operations | For multi-step workflows instead of chaining Lambdas. |
| Google Cloud Run | 60 min per request; jobs 7 days (1 h with GPU) | up to 8 vCPU, 32 GiB per instance | 32 MiB HTTP/1 request/response (use HTTP/2 or streaming for more) | Concurrency up to 1,000 per instance (default 80). Min instances = no cold start, billed while idle. |
| Azure Functions Flex Consumption | default 30 min, no enforced max (60 min grace on scale-in) | 512 MB / 2 GB / 4 GB instances | 210 MB request | **HTTP response must start within 230 s** (load balancer). Linux only. Up to 1,000 instances. Always-ready instances remove cold start. |
| Azure Functions Consumption (legacy) | 5 min default, 10 min max | 1.5 GB | - | Linux Consumption retires **2028-09-30**, v3 runtime on Linux Consumption stops **2026-09-30**. Use Flex. |
| Cloudflare Workers | HTTP: no wall limit while client connected; Cron/Queue consumers 15 min | 128 MB per isolate; CPU 10 ms (Free) / 30 s default, 5 min max (Paid) | script 64 MiB; 50 (Free) / 10,000 (Paid) subrequests | Free: 100,000 req/day. Paid $5/month min, 10 M req + 30 M CPU-ms included. V8 isolates: no native binaries, no raw TCP server, Node APIs via `nodejs_compat`. |
| Durable Objects | CPU 30 s default, 5 min max per request | SQLite storage 10 GB per object (Paid), 1 GB (Free) | 2 MB key+value | One object = single-threaded, strongly consistent; SQLite backend on Free; KV backend legacy. |
| D1 | 30 s per query | single-threaded per DB | 10 GB per DB (Paid), 500 MB (Free); 1 TB per account | Throughput = 1/query time. Shard per tenant for scale. |
| Workers KV | - | - | value 25 MiB, key 512 B | **1 write/s per key**; eventually consistent (reads elsewhere can be stale ~60 s+). Free: 100k reads, 1k writes/day. Not a database. |
| Queues | consumer 15 min wall | CPU 30 s default, 5 min max | 128 KB message, 5,000 msg/s per queue | Retention up to 14 days (24 h on Free). At-least-once: make consumers idempotent. |
| R2 | - | - | - | $0.015/GB-month Standard, $0.01 IA (30-day minimum + $0.01/GB retrieval); **zero egress**; Class A $4.50/M, Class B $0.36/M; free 10 GB, 1 M A, 10 M B. S3-compatible API (not every S3 feature). |
| Cloudflare Containers | - | lite 1/16 vCPU 256 MiB ... standard-4 4 vCPU 12 GiB 20 GB disk; custom types up to 4 vCPU | - | GA 2026-04-13 on Workers Paid; driven from a Worker/Durable Object; disk is ephemeral. |
| Vercel Functions (Fluid compute) | 300 s default; Pro/Enterprise max 800 s (1,800 s beta) | per plan | 4.5 MB body (unverified) | Active CPU pricing: billed for CPU time, not I/O wait. |

## Cloudflare product cheat sheet: can / cannot

- **Workers**: request handlers at the edge, cron, queue consumers. Cannot: long CPU (>5 min), native modules, filesystem, listening sockets. Outbound TCP via `connect()`.
- **Pages**: still works, but new full-stack projects go to **Workers with static assets** (Cloudflare steers there; Pages gets fewer features) (unverified wording).
- **R2**: object store, zero egress, presigned URLs, event notifications to Queues. Cannot: object-level ACLs like S3 (bucket public or token-scoped), Glacier-style tiers.
- **D1**: small relational per-tenant/per-app DBs, Time Travel point-in-time restore (30 days on Paid, unverified). Cannot: big single DB, high write concurrency.
- **KV**: read-heavy config, flags, cache. Cannot: counters, anything needing read-after-write across locations.
- **Durable Objects**: per-entity state, WebSockets (hibernation API), rate limiters, rooms, locks, alarms. Design one object per entity, not one global object.
- **Queues**: async jobs, batching, retries, DLQ. Cannot: >128 KB messages (store in R2, pass the key).
- **Tunnel (cloudflared)**: publish a private service with no open inbound port; pair with **Access** (Zero Trust) for SSO in front of admin panels. Free Zero Trust plan covers small teams (50 users, unverified).
