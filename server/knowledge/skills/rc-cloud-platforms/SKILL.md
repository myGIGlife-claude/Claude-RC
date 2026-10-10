---
name: rc-cloud-platforms
description: Choosing and using cloud platforms - AWS, Azure, Google Cloud, Cloudflare (Workers, R2, D1, KV, Durable Objects, Tunnel), OVHcloud, IBM Cloud, plus Hetzner, DigitalOcean, Fly.io, Vercel, Netlify, Railway, Render, Supabase, Firebase. Use for picking a host, service mapping, IAM/OIDC from CI, serverless limits, egress/NAT cost traps, budgets, EU data residency, lock-in and DR. Triggers: wrangler.toml, serverless.yml, vercel.json, fly.toml, *.tf.
---
# Cloud platforms: choose, secure, pay less (as of 2026-10)

> Facts are dated (see Sources). Clouds rename products and change limits monthly: confirm on the provider's docs before pinning a
> number in code or a quote. Anything marked (unverified) is a lead, not a fact.

Scope: which platform and which service, how to set up accounts/IAM, what it costs, how to leave. Server admin, Docker/K8s, CI pipelines,
Terraform/OpenTofu: **rc-devops-linux**. Database design: **rc-databases**. Supabase details: the `supabase` skill. Service mapping table
and serverless/edge limits: `references/service-map.md`.

## Currency check

| Thing | State (verified 2026-10-09) |
|---|---|
| AWS Free Tier | Since 2025-07-15 (date unverified) new accounts: **Free plan** with up to $200 credits for 6 months (no charges; account closes after 6 months or when credits run out unless upgraded) or **Paid plan**. 30+ always-free offers remain. The old "12 months free t2.micro" advice is wrong for new accounts. |
| AWS European Sovereign Cloud | **GA 2026-01-14**, Brandenburg (DE), partition `aws-eusc`, Region `eusc-de-east-1`; EU-resident operators; separate accounts/IAM from global AWS. Sovereign Local Zones planned (BE, NL, PT). |
| AWS Lambda | 15 min max, 10,240 MB, 6 MB sync payload, 200 MB streamed response. New in the last year: **durable functions** (checkpointed multi-step), **Lambda Managed Instances** (up to 90 min async), **Lambda MicroVMs** (up to 8 h, Graviton). New accounts get reduced concurrency quotas that grow with use. |
| AWS VPC | NAT gateway $0.045/h + **$0.045/GB processed** (us-east-2 example); **regional NAT gateway** bills per AZ; every public IPv4 $0.005/h (in use or idle, since 2024-02). 100 GB/month internet egress free across AWS. |
| Azure Functions | **Flex Consumption** is the serverless default. Consumption plan is legacy; **Linux Consumption retires 2028-09-30**; v3 runtime on Linux Consumption stopped 2026-09-30. HTTP responses still cap at 230 s. |
| Azure sovereignty | EU Data Boundary completed 2025-02-26; Microsoft Sovereign Cloud (Sovereign Public Cloud, Sovereign Private Cloud on Azure Local incl. disconnected). |
| Google Cloud | Docs moved to `docs.cloud.google.com`. Cloud Run: 60 min requests, 7-day jobs, 8 vCPU/32 GiB, GPUs. "Cloud Functions" is now **Cloud Run functions**. Orgs created since **2024-05-03** block service-account key creation/upload by default. Sovereign: Data Boundary (Assured Workloads), **Google Cloud Dedicated** (S3NS in France, SecNumCloud target, preview), Distributed Cloud air-gapped. |
| Cloudflare | Workers Paid CPU up to **5 min** per request; Containers + Sandbox SDK **GA 2026-04-13**; D1 10 GB per DB; Durable Objects SQLite on Free; R2 zero egress. |
| OVHcloud | **Object Storage egress free since 2026-01-01** (incl. API calls). VPS range 2 to 8 vCores, unmetered traffic, anti-DDoS included. |
| IBM Cloud | **IBM Sovereign Core GA 2026-05-05** (customer-operated sovereign control plane, Red Hat based); Sovereignty Risk Profile (2026-05). |
| Hetzner | Price increases **2026-04-01** and **2026-06-15** (dedicated-vCPU CPX/CCX up to +176% for new orders); EU cloud servers still include >= 20 TB traffic (secondary sources). |
| EU Data Act | Applies since **2025-09-12**; cloud providers must drop **all switching and egress-for-switching charges from 2027-01-12**. |

### Older versions (what differs on legacy setups)
- **AWS accounts opened before 2025-07-15** keep the old 12-month free tier until it ends: do not assume the new $200-credit Free plan.
- **Azure Linux Consumption apps** still run until 2028-09-30 but get no new language versions: plan the Flex move, do not start new apps there.
- **GCP orgs older than 2024-05-03** may still allow service-account keys and auto-grant Editor to default service accounts: enforce `iam.disableServiceAccountKeyCreation` and `iam.automaticIamGrantsForDefaultServiceAccounts` yourself.
- **Cloudflare Workers on the old Bundled/Unbound models** and KV-backed Durable Objects: legacy; new code uses Standard pricing and SQLite-backed DOs.
- **Terraform providers pinned years back** may use pre-rename resource names (e.g. `google_cloudfunctions_function` v1). Change only if the task asks.

## What changed / stop doing

| Stop (old) | Do instead (new) | Since |
|---|---|---|
| IAM users with long-lived access keys in CI or on laptops | SSO for people (IAM Identity Center / Entra ID / Cloud Identity), roles + **OIDC federation** from CI (`id-token: write`), attached roles on compute | AWS/GCP/Azure all GA for years; GCP blocks SA keys by default since 2024-05 |
| Public S3/Blob/GCS buckets for "a few files" | Block Public Access on (AWS default for new buckets since 2023-04), serve through CDN with origin access control, presigned URLs | 2023 |
| One giant account/subscription/project for everything | AWS Organizations (accounts per env), Azure management groups + subscriptions, GCP folders + projects; SCPs/RCPs or org policies as guardrails | - |
| Always-on dev/staging stacks | Scale to zero, schedules that stop dev at night, ephemeral preview environments torn down on merge | - |
| "Serverless is always cheaper" | Serverless wins on spiky/low traffic; steady high load is cheaper on containers/VMs. Do the math (procedure below). | - |
| NAT gateway for every private subnet that talks to S3/DynamoDB | Gateway VPC endpoints (free) for S3/DynamoDB, interface endpoints where volume justifies, IPv6 + egress-only IGW | - |
| Azure Functions Consumption (Linux) for new apps | Flex Consumption | Flex GA 2024-11; Linux Consumption retirement 2028-09-30 |
| Cloud Functions gen1 | Cloud Run functions | 2024-08 rename |
| Root user / global admin for daily work | Root locked away with MFA (passkey/security key), centralized root access in Organizations, break-glass procedure | AWS centralized root access 2024-11 |
| Paying hyperscaler egress for downloads/media | R2 (zero egress), OVHcloud Object Storage (free egress 2026), CDN in front | - |
| Trusting free tiers to stay free | Read the cliff: Supabase Free pauses after 1 week idle; Fly.io has a trial only; Cloudflare KV Free 1,000 writes/day | - |

## Do this

### Account structure (first day, any cloud)
- **AWS**: Organization with management account doing nothing but billing; OUs `security`, `prod`, `nonprod`, `sandbox`; one account per env (per app when teams grow). IAM Identity Center for people. SCPs: deny leaving org, deny disabling CloudTrail/GuardDuty, deny unused Regions. RCPs to stop data leaving the org. Org-wide CloudTrail to a log-archive account.
- **Azure**: Entra ID tenant, management groups -> subscriptions per env; Azure Policy for allowed regions/SKUs; PIM for just-in-time admin; managed identities on every app; RBAC at resource-group scope.
- **Google Cloud**: Organization -> folders (prod/nonprod) -> projects per app-env; org policies (no SA keys, no default-SA Editor, domain-restricted sharing, resource location restriction to `in:eu-locations` when needed); groups not individual users in IAM bindings.
- **Cloudflare**: account members with SSO, scoped **API tokens** (one per job, zone/account-limited, IP filter, expiry), never the Global API Key.
- **OVHcloud / IBM / smaller hosts**: enable 2FA on the account, use sub-users or IAM policies for automation, separate projects per env, API credentials scoped and rotated.

### Least privilege and keyless CI
- People: SSO + MFA (phishing-resistant), short sessions, admin only via elevation.
- Workloads: the platform identity (AWS role on Lambda/ECS/EC2, Azure managed identity, GCP attached service account). No keys in env vars.
- CI: OIDC federation. Restrict the trust to repo **and** branch/environment, never `repo:org/*`.

```yaml
# GitHub Actions -> AWS, no stored keys
permissions: { id-token: write, contents: read }
steps:
  - uses: aws-actions/configure-aws-credentials@<pinned-sha>
    with: { role-to-assume: arn:aws:iam::<acct>:role/deploy-prod, aws-region: eu-central-1 }
```
Trust policy condition: `token.actions.githubusercontent.com:sub` = `repo:<org>/<repo>:environment:prod` and `aud` = `sts.amazonaws.com`.
GCP: Workload Identity Federation pool + provider with `attribute.repository` condition. Azure: federated credential on a user-assigned managed identity or app registration.
- Start from managed policies, then tighten with IAM Access Analyzer policy generation (AWS), Recommender (GCP), access reviews (Azure).

### Choosing compute (default ladder)
1. Static files? CDN/static host (Cloudflare, Netlify, Vercel, S3+CloudFront). No server.
2. Request/response API with spiky or low traffic: serverless (Workers, Cloud Run, Lambda, Flex Consumption). Check limits in `references/service-map.md`.
3. Container you already have, steady traffic: Cloud Run / Container Apps / ECS Fargate / Fly.io / Railway / Render.
4. Long-running, stateful, GPU, or cost-sensitive steady load: VMs (Hetzner, OVHcloud, DigitalOcean, EC2, GCE). Ops cost is yours (see rc-devops-linux).
5. Kubernetes only with a team that can run it and >5-10 services that need it.

### Serverless: limits and cold starts
- **Cold start** is the time to create a sandbox + load your code. Edge isolates (Workers) are near-zero; Lambda/Cloud Run/Functions range from ~100 ms (small Node/Go/Python) to seconds (JVM, big images). Fixes: smaller bundles, lazy imports, Lambda **SnapStart** (Java, Python, .NET), provisioned concurrency / Cloud Run min instances / Flex always-ready (these cost money while idle).
- Hard limits that break designs: Lambda 15 min and 6 MB sync payload; Azure HTTP 230 s; Cloud Run 60 min request; Workers 128 MB memory and 10 ms CPU on Free; KV 1 write/s per key; Queues 128 KB message.
- Long work: return 202 + queue + worker; or durable workflows (Step Functions, Lambda durable functions, Durable Functions, Cloudflare Workflows, Cloud Run jobs).
- DB connections from serverless: use a pooler (RDS Proxy, Hyperdrive, Supabase pooler, PgBouncer) or an HTTP-based driver.

### Edge runtimes
- Edge (Workers, Vercel/Netlify edge functions, CloudFront Functions/Lambda@Edge) = Web-standard APIs (`fetch`, `Request`, Web Crypto, streams), small memory, no native addons. Put auth checks, redirects, A/B, caching, geo routing there; keep heavy compute and DB-chatty code near the database (one region) or the latency win disappears.
- Smart placement / regional pinning: run the function near its data, not near the user, when it makes several DB calls.

### Regions, residency, sovereignty (EU)
- Residency (data stored in region X) is a config choice: pick EU regions, set org policy/Azure Policy/SCP to deny others, check backups, logs and support tooling also stay in region.
- Sovereignty (who can be compelled to access it) is different: US-headquartered clouds remain subject to US law (CLOUD Act) even in EU regions. Options by strength: EU region + customer-managed keys (CMK/EKM/HYOK) -> provider sovereign offerings (AWS European Sovereign Cloud, Microsoft Sovereign Cloud, Google Cloud Data Boundary / Dedicated) -> EU-headquartered providers (OVHcloud, Hetzner, Scaleway, IONOS) -> on-prem/air-gapped.
- Certifications to ask for (download the report, check scope covers the service and region): ISO 27001/27017/27018, SOC 2 Type II, **C5** (DE), **SecNumCloud** (FR, OVHcloud has qualified offers), ENS (ES), HDS (FR health), PCI DSS v4.0, HIPAA BAA (US health), FedRAMP (US gov), CSA STAR. Regulated EU finance: **DORA** (applies since 2025-01-17) needs exit plans and register of ICT providers; NIS2 for essential entities. Sign the provider DPA; check sub-processor list and transfer mechanism (SCCs / EU-US Data Privacy Framework).

### OVHcloud specifics
- Products: **VPS** (fixed monthly, unmetered traffic, 250 Mbps-2 Gbps depending on plan), **Public Cloud** (OpenStack instances, hourly or monthly billing, Managed Kubernetes, Managed Databases, Object Storage S3-compatible), **Bare Metal** dedicated servers (Eco: Kimsufi/So you Start/Rise ranges).
- Provision **key-only**: store your public SSH key in the Control Panel, pick it under "SSH key to pre-install" when (re)installing a VPS, and tick the option to not receive credentials by email. Then confirm `PasswordAuthentication no` on the box (cloud-init images may re-enable it; see rc-devops-linux).
- Reinstall wipes the disk: snapshot or back up first. VPS backups/snapshots are paid options; automate offsite backups to Object Storage in another region.
- Anti-DDoS (VAC) included on all products; Edge Network Firewall/game firewall configurable on IPs. Traffic is unmetered on VPS and in most Public Cloud regions (APAC regions may bill outbound, unverified). Object Storage egress free since 2026-01-01.
- OVHcloud API (`api.ovh.com` / EU endpoint) with application keys + consumer key scoped to routes; IAM policies for sub-users.

### IBM Cloud positioning
- Pick it for: regulated finance (IBM Cloud for Financial Services framework, unverified current branding), workloads on IBM Z/LinuxONE and Power (Power Virtual Server for AIX/IBM i), VMware lift-and-shift, Red Hat OpenShift managed, keep-your-own-key HSM (Hyper Protect Crypto Services, FIPS 140-2 Level 4, unverified), and sovereign deployments (Sovereign Core).
- Not the pick for: startups, hobby projects, serverless-first apps; smaller community, fewer managed services, docs/examples thinner.

### App platforms (one line of judgment each)
- **Hetzner**: cheapest serious EU VMs and dedicated servers, 20 TB traffic included, simple firewall/LB/volumes/object storage; no managed DB, few managed services. 2026 price rises narrowed the gap but it is still cheap. You run ops.
- **DigitalOcean**: simple VMs (Droplets), App Platform (PaaS), managed Postgres/MySQL/Valkey, Spaces (S3-compatible + CDN), managed K8s; predictable pricing with bundled transfer. Good middle ground for small teams.
- **Fly.io**: Firecracker micro-VMs near users, per-second billing, stopped machines pay only rootfs ($0.15/GB-month), volumes $0.15/GB-month, egress $0.02/GB NA/EU; **no free tier** (trial: 2 h runtime or 7 days). Volumes are single-host: you own replication/backups.
- **Vercel**: best fit for Next.js; Fluid compute with **Active CPU** pricing (you pay for CPU, not I/O wait); functions 300 s default, 800 s max on Pro. Watch bandwidth, image optimization and function costs at scale; set spend management.
- **Netlify**: static/Jamstack and framework sites, functions + edge functions; credit-based pricing since 2025 (unverified details). Good for marketing sites and docs.
- **Railway / Render**: Heroku-style PaaS: git push, services + managed Postgres/Redis, preview environments; Render has a free web tier that sleeps (unverified), Railway is usage-based. Fine for MVPs; check DB backup/PITR tier before production.
- **Supabase**: Postgres + Auth + Storage + Realtime + Edge Functions; Free: 500 MB DB, 50k MAU, 5 GB egress, 2 projects, **paused after 1 week idle**; Pro from $25/month with spend cap on by default. RLS on every table (see `supabase` skill).
- **Firebase**: Firestore/Auth/Hosting/Cloud Functions/FCM, best for mobile realtime; Blaze plan needed for Functions; set budget alerts (budgets alert, they do not cap) and Security Rules + App Check.

### Managed vs self-hosted
Prefer managed when: data loss would hurt (databases, object storage, queues), you have < 1 person-day/month for ops, you need HA/PITR/patching, or compliance needs audited controls. Self-host when: steady load where managed costs > 3x the VM, the managed version lacks a feature you need, sovereignty forbids the provider, or it is stateless and trivially rebuilt. Self-hosting a database means you own backups, restore drills, upgrades, failover and monitoring: write that runbook before choosing it.

### Lock-in and honest multi-cloud
- Lock-in is in data gravity, IAM, proprietary databases (DynamoDB, Firestore, Cosmos DB, D1/DO) and event wiring, not in VMs.
- Cheap exits: Postgres/MySQL instead of proprietary DBs when portability matters, S3-compatible object API, OCI containers, OpenTelemetry, IaC (OpenTofu/Terraform), OIDC.
- Price the exit: data volume x egress rate + rewrite of proprietary services + re-certification. Under the EU Data Act, switching egress charges must go by 2027-01-12; AWS, Google and Azure already waive egress for customers leaving (application required, unverified current terms).
- Multi-cloud for one app usually doubles ops and halves expertise. Honest uses: DNS/CDN on Cloudflare + compute elsewhere, backups in a second provider, best-of-breed AI APIs, regulated workload in a sovereign provider. Active-active across clouds only with a strong reason and a team.

### Backup and DR per provider
- **AWS**: AWS Backup (cross-account + cross-Region copies, Vault Lock for immutability, logically air-gapped vaults); RDS/Aurora PITR; S3 versioning + Object Lock + replication.
- **Azure**: Azure Backup (immutable + soft-delete vaults), Site Recovery, geo-redundant storage (GRS/GZRS), DB PITR.
- **Google Cloud**: Backup and DR Service (backup vaults with enforced retention), Cloud SQL PITR + cross-region replicas, dual/multi-region buckets, Object Retention Lock.
- **Cloudflare**: D1 Time Travel, R2 has no versioning by default (unverified 2026): copy to a second bucket/provider; Durable Objects point-in-time recovery for SQLite storage (unverified window).
- **OVHcloud**: VPS automated backup option, Public Cloud instance snapshots/volume backups, Managed DB backups, Object Storage with Object Lock and replication (unverified feature set per region).
- **Hetzner**: server backups (7 slots) and snapshots in the same location: add an offsite copy.
- Rule: 3-2-1 with one copy in another account or provider, immutable, encrypted, and a restore test on a schedule. Define RPO/RTO first, then pick the feature.

### Pick a platform (by project type)
| Project | Default pick | Why / alternatives |
|---|---|---|
| Static site, docs, landing page | Cloudflare (Workers static assets / Pages) or Netlify | Free bandwidth tiers, global CDN. Vercel if it is Next.js. |
| API + Postgres, small team | Cloud Run + Cloud SQL, or Fly.io/Render/Railway + managed Postgres, or Hetzner VM + managed backups | Containers, no K8s; pick by region and budget. |
| Mobile backend | Supabase or Firebase | Auth, DB, storage, push in one; check pricing cliffs and RLS/Rules. |
| AI app (LLM calls, RAG) | Serverless with streaming (Workers, Vercel Fluid, Cloud Run) + Postgres with pgvector; GPUs on demand (Cloud Run GPU, managed inference) | LLM calls are I/O wait: Active-CPU or isolate billing is cheap; GPU VMs idle are expensive. |
| Enterprise / existing Microsoft estate | Azure (Entra ID), or AWS with landing zone | Follow where identity and contracts already are. |
| Hobby / side project | Cloudflare free tier, Hetzner/OVHcloud VPS, Supabase free (accept pause) | Set a budget alert on day one. |
| EU-regulated (health, public sector, finance) | EU provider with C5/SecNumCloud/HDS as required (OVHcloud, IONOS, Scaleway), or a hyperscaler sovereign offering | Map certification to the specific service + region; DORA exit plan. |

### Cost estimation procedure
1. List drivers per component: requests/month, avg duration and memory, storage GB, egress GB (to internet, cross-AZ, cross-region), log GB/month, IPs, NAT GB.
2. Price with the official calculator (AWS Pricing Calculator, Azure Pricing Calculator, Google Cloud Pricing Calculator, provider price pages). Include free-tier but also price the month after it ends.
3. Add 20-30% for things you forgot (logs, snapshots, IPv4, support plan).
4. Compare serverless vs always-on at expected **and** 10x traffic.
5. Write the estimate and assumptions into the repo (README or docs/costs.md) so the next session can check drift.
6. Day one: budget at 50/80/100% of estimate + anomaly detection (AWS Cost Anomaly Detection, Azure anomaly alerts, GCP budget -> Pub/Sub). Remember budgets alert; only some platforms hard-cap (Supabase spend cap, Vercel spend management, Cloudflare Free limits).
7. Tag/label every resource with `app`, `env`, `owner` for cost allocation.

## Security
- **Leaked keys** are the top cloud incident: no long-lived keys (OIDC, roles, managed identities), secret scanning + push protection on the repo, and an automatic key-revocation runbook.
- **Public storage**: keep account-level Block Public Access / public access prevention on; serve via CDN with origin access control; presigned URLs with short expiry.
- **Over-broad IAM**: no `*:*`, no `Owner`/`Editor` on service accounts, no `roles/editor` default grants; review with Access Analyzer / IAM Recommender / Entra access reviews.
- **Metadata endpoint SSRF**: IMDSv2 required on EC2 (hop limit 1 for containers); GCE/Azure metadata need headers but SSRF can still reach them: block 169.254.169.254 from user-controlled fetches.
- **Logs**: org-wide audit logs (CloudTrail, Azure Activity Log, GCP Admin Activity) to a separate locked account/project; Data Access logs selectively (they cost).
- **Detection**: GuardDuty/Security Hub, Defender for Cloud, Security Command Center, Cloudflare Security Center: turn on the free tiers at least.
- **Admin panels**: never on the public internet; Cloudflare Tunnel + Access, IAP (GCP), Bastion/SSM Session Manager.
- **Encryption**: on by default everywhere; use CMK only when you need key control (it adds cost and the risk of locking yourself out).

## Performance & quality
- Put the compute in the same region (and ideally AZ) as the database; measure p95 latency of the DB round trip from the function.
- Cold-start budget: measure p99 for the first request after idle; if it breaks your SLO, pay for min instances on the hot path only.
- Cost KPIs per month: cost per 1,000 requests, cost per active user, egress GB, log GB ingested, idle resources count. Review monthly.
- Cross-AZ traffic costs money on AWS/GCP/Azure (typically ~$0.01/GB each way on AWS, unverified current rate): keep chatty services and replicas aware of topology.

## Testing & tooling
- CLIs: `aws` (v2), `az`, `gcloud`, `wrangler` (Cloudflare), `ovhcloud`/OVH API, `ibmcloud`, `flyctl`, `vercel`, `supabase`.
- Local emulators: `wrangler dev` (Miniflare), Firebase Emulator Suite, `supabase start`, LocalStack (AWS, note: licensing changed, check current terms, unverified), Azurite (Azure Storage).
- Policy-as-code and checks: Checkov/Trivy for IaC misconfig, `aws accessanalyzer validate-policy`, Prowler/ScoutSuite for account posture, Infracost for cost diff on IaC PRs.
- Keep infra in IaC (OpenTofu/Terraform, see rc-devops-linux); console clicks only in sandbox accounts.

## Common mistakes in AI-written code
- Writing `aws_access_key_id`/`AWS_SECRET_ACCESS_KEY` into CI secrets or `.env` instead of OIDC/role assumption.
- Wildcard IAM (`"Action": "*"`, `"Resource": "*"`) "to get it working"; GCP `roles/owner` on a deploy SA.
- Using Workers KV as a database or counter (1 write/s per key, eventually consistent): use Durable Objects or D1.
- Assuming Node built-ins/native modules work on Workers/edge; long CPU loops on Workers Free (10 ms).
- Lambda behind API Gateway returning >6 MB or running >29 s on REST API integration (API Gateway integration timeout default is 29 s, raisable for Regional/private APIs, unverified current max).
- Calling Cloud Functions "gen2" or using deprecated `gcloud functions deploy` flags; Azure docs for the Consumption plan copied into a Flex app (`WEBSITE_CONTENTAZUREFILECONNECTIONSTRING` does not apply on Flex).
- NAT gateway in every AZ for a dev VPC that only talks to S3 (use a free gateway endpoint).
- Quoting old free tiers (AWS 12-month t2.micro, Heroku free dynos, Fly.io free allowance) or old prices.
- Picking `us-east-1` by default for an EU product (residency + latency).
- Forgetting to delete preview stacks, unattached volumes/IPs, old snapshots: they bill forever.
- Opening `0.0.0.0/0` on SSH/DB ports in a security group "temporarily".

## Before you ship
- [ ] Accounts/projects split by env; root/global admin locked with MFA; break-glass documented.
- [ ] No long-lived keys: SSO for people, roles/managed identities for workloads, OIDC from CI restricted to repo + environment.
- [ ] Storage private, public access prevention on; DB not reachable from the internet; admin UIs behind SSO/tunnel.
- [ ] Region choice documented (residency, latency); org policy denies other regions if required.
- [ ] Limits checked against the design (timeouts, payload, memory, per-key writes) with headroom.
- [ ] Cost estimate written; budgets + anomaly alerts on; tags on all resources; dev scales to zero or stops nightly.
- [ ] Backups: automated, cross-account/provider copy, immutable, restore tested; RPO/RTO written.
- [ ] Audit logs on and shipped to a separate place; threat detection free tier enabled.
- [ ] Exit noted: what is proprietary, how data leaves, rough exit cost.
- [ ] Rollback path for deploys (previous version/alias/traffic split) tested.

## Sources
- https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html - Lambda quotas, durable functions, Managed Instances, MicroVMs (2026-10-09)
- https://aws.amazon.com/free/ - Free plan/Paid plan, $200 credits, 6 months (2026-10-09)
- https://aws.amazon.com/vpc/pricing/ - NAT gateway, regional NAT, public IPv4 prices (2026-10-09)
- https://aws.amazon.com/ec2/pricing/on-demand/ - 100 GB/month free internet egress (2026-10-09)
- https://aws.amazon.com/blogs/aws/opening-the-aws-european-sovereign-cloud - ESC GA 2026-01-14, region, operators (2026-10-09)
- https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html - federation, temporary credentials, SCP/RCP, Access Analyzer (2026-10-09)
- https://learn.microsoft.com/en-us/azure/azure-functions/functions-scale - Flex Consumption, timeouts, 230 s HTTP, Linux Consumption retirement (2026-10-09)
- https://blogs.microsoft.com/on-the-issues/2025/02/26/microsoft-completes-landmark-eu-data-boundary-offering-enhanced-data-residency-and-transparency/ - EU Data Boundary complete (2026-10-09)
- https://learn.microsoft.com/en-us/azure/azure-sovereign-clouds/microsoft-sovereign-cloud - Microsoft Sovereign Cloud (2026-10-09, search lead)
- https://docs.cloud.google.com/run/quotas - Cloud Run limits (2026-10-09)
- https://docs.cloud.google.com/resource-manager/docs/organization-policy/restricting-service-accounts - SA key creation blocked by default since 2024-05-03 (2026-10-09)
- https://cloud.google.com/sovereign-cloud - Data Boundary, Dedicated (S3NS), air-gapped (2026-10-09)
- https://developers.cloudflare.com/workers/platform/limits/ and /workers/platform/pricing/ - Workers limits and pricing (2026-10-09)
- https://developers.cloudflare.com/d1/platform/limits/ , /durable-objects/platform/limits/ , /kv/platform/limits/ , /queues/platform/limits/ , /r2/pricing/ - Cloudflare data products (2026-10-09)
- https://developers.cloudflare.com/changelog/post/2026-04-13-containers-sandbox-ga/ and /containers/platform-details/limits/ - Containers GA and instance types (2026-10-09)
- https://www.ovhcloud.com/en/public-cloud/object-storage/ - no egress fees, pricing (2026-10-09)
- https://www.ovhcloud.com/en/vps/unmetered-vps/ - VPS unmetered traffic, anti-DDoS (2026-10-09)
- https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/understand-vps-control-panel - reinstall with pre-installed SSH key, no credentials email (2026-10-09)
- https://newsroom.ibm.com/2026-05-05-think-2026-ibm-makes-digital-sovereignty-operational-with-general-availability-of-ibm-sovereign-core - IBM Sovereign Core GA (2026-10-09)
- https://docs.fly.io/about/pricing - Fly.io trial, machine/volume/egress prices (2026-10-09)
- https://vercel.com/docs/functions/usage-and-pricing and https://vercel.com/docs/functions/limitations - Active CPU, durations (2026-10-09)
- https://supabase.com/pricing - Free plan limits, 1-week pause, Pro $25 (2026-10-09)
- https://digital-strategy.ec.europa.eu/en/factpages/data-act-explained - Data Act switching charges end 2027-01-12 (2026-10-09)
- https://docs.github.com/en/actions/concepts/security/openid-connect - OIDC to AWS/Azure/GCP (2026-10-09)
- https://northflank.com/blog/hetzner-cloud-server-price-increases - Hetzner 2026 price changes (secondary source, 2026-10-09)
