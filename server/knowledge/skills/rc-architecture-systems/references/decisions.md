# Architecture decision tables (as of 2026-10)

Companion to `../SKILL.md`. Versions are in the SKILL.md Currency check.

## Messaging and streaming matrix

| | Kafka 4.x | RabbitMQ 4.x | NATS JetStream | SQS/SNS/EventBridge | GCP Pub/Sub | Redis/Valkey Streams | Postgres queue |
|---|---|---|---|---|---|---|---|
| Model | Partitioned log; consumer groups; share groups (queue) since 4.2 | Queues + exchanges routing; quorum queues; streams | Subjects + persistent streams; work-queue/limits/interest retention | Managed queue; pub/sub fan-out; event bus with rules | Managed topic/subscription | Append-only stream in memory store | Table + `FOR UPDATE SKIP LOCKED` |
| Replay | Yes (retention, compaction) | Streams only | Yes | No (EventBridge archive/replay yes) | Seek/snapshots | Yes (bounded by memory) | If you keep rows |
| Ordering | Per partition | Per queue (single consumer) | Per subject/stream | FIFO queues per message group | Ordering keys | Per stream | Your query |
| Throughput | Very high | High | High, low latency | Effectively unlimited (standard) | Very high | High, memory-bound | Moderate (thousands/s) |
| Ops cost self-hosted | High | Medium | Low | None (managed) | None | Low if already run | None extra |
| Use for | Event backbone, CDC, stream processing, analytics feeds | Task queues, complex routing, RPC-ish, priorities | Edge/IoT, request-reply, light streaming | AWS-native jobs and fan-out | GCP-native events | Small-scale streams with existing Redis/Valkey | Jobs inside one app |
| Avoid when | Small team, simple jobs | Need long replay at scale | Need Kafka ecosystem (Connect, Streams) | Multi-cloud portability is required | Outside GCP | Durability must survive node loss without care | Cross-app integration, very high rates |

Licensing notes: Kafka, RabbitMQ (MPL-2.0), NATS, Pulsar are open source; Redis 8 is RSAL/SSPL/AGPL, Valkey BSD-3; Redpanda core is BSL 1.1 and its enterprise features are under the Redpanda Community License (neither is OSI open source).
Delivery: all are at-least-once in practice for side effects. Kafka transactions give exactly-once only for consume-transform-produce within Kafka. Pub/Sub exactly-once delivery is scoped to Pub/Sub acks, not your database: pull subscriptions only, subscribers in the same region, and publish-side retries can still create duplicates.

## Payments flow (hosted checkout, Stripe-style)

1. Client asks your server to start checkout for cart X.
2. Server loads prices from its DB, creates an internal `order` (status `pending`), creates a Checkout Session (or PaymentIntent) with
   `metadata.order_id`, `Idempotency-Key` = order id + attempt, pinned API version. Returns the session URL / client secret.
3. Customer pays on the provider's page or Payment Element; provider runs 3DS/SCA when required.
4. Provider redirects to your success page: show "processing", do NOT fulfil here.
5. Webhook `checkout.session.completed` / `payment_intent.succeeded`: verify signature (raw body), dedupe by event id (inbox table),
   load order by metadata, transition `pending -> paid` with a conditional update (`WHERE status = 'pending'`), enqueue fulfilment via outbox.
6. Handle also: `payment_intent.payment_failed`, `async_payment_succeeded/failed` (bank debits are delayed), `charge.refunded`, `charge.dispute.created`,
   `invoice.paid` / `invoice.payment_failed` / `customer.subscription.updated|deleted` for subscriptions.
7. Nightly reconciliation: compare provider balance transactions/payouts with orders; alert on mismatch.
- Subscriptions: let the provider own billing cycles, proration, dunning (Smart Retries) and the customer portal; mirror state from webhooks.
- Off-session charges failing with `authentication_required`: notify the customer with a link to authenticate; do not loop retries.
- Marketplaces/payouts: use the provider's connected-accounts product (Stripe Connect) for KYC and fund flows; do not hold funds yourself (licensing).
- Tax: use the provider's tax engine or a dedicated service; store tax lines per order.
- Test: provider test cards for 3DS required, declined, insufficient funds, disputes; CLI webhook forwarding; replay the same event twice.

## HA / DR tiers

| Tier | RPO | RTO | Setup | Relative cost |
|---|---|---|---|---|
| Backup and restore | hours (or minutes with PITR) | hours to a day | Backups + IaC to rebuild in another region | 1x |
| Pilot light | minutes | tens of minutes to hours | Data replicated to region B, compute off, scale up on failover | ~1.2x |
| Warm standby (active-passive) | seconds to minutes | minutes | Reduced-size full stack running in B, async replication, DNS/GSLB failover | ~1.5-2x |
| Active-active | ~0 for region-owned data | ~0 (automatic) | Full stack in each region, global routing, conflict resolution or per-region data ownership | 2x+ and much harder ops |

- Multi-AZ inside one region (managed DB with standby, 3 AZs for quorum systems) is the default HA step and covers most outages.
- Async replication = data loss window on failover; sync cross-region = write latency (PACELC).
- Failover checklist: who decides, how DNS/traffic moves, how writes are fenced in the old primary, how you fail back, which third parties are region-bound.
- Exercise at least twice a year; record achieved RPO/RTO.

## ADR template

```markdown
# NNNN. <Decision title>
Status: proposed | accepted | superseded by NNNN   Date: YYYY-MM-DD
## Context
The forces: load numbers, team, deadlines, compliance, current pain.
## Options
1. <Option> - pros / cons / cost / reversibility
2. ...
## Decision
What we chose and the deciding reason.
## Consequences
What gets easier, what gets harder, follow-up work, how we will know it was wrong (a metric or trigger to revisit).
```

## Service extraction readiness (all should be "yes")
- The module already has its own schema and no other module reads its tables.
- Its API is used in-process through one interface and has contract tests.
- There is a named owning team with on-call.
- CI/CD, logs, traces, dashboards and alerts exist for a new deployable.
- The data migration and rollback (route back to the monolith) are planned.
- The reason (team, scale, isolation, tech) is written in an ADR with a metric.
