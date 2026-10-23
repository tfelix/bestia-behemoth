---
name: observability
description: The dev Prometheus + Grafana stack for zone-server, login-server and api-service - how to start it, where each service exports its meters, how to query Prometheus and Grafana from a shell or the in-app browser, how Micrometer meter names become Prometheus series, and how to add a metric or a dashboard. Read this BEFORE debugging lag, rubber-banding, queue growth or a slow endpoint, before adding a Micrometer meter, or before editing a dashboard. Triggers on: metrics, Grafana, Prometheus, dashboard, meter, tick duration, dropped steps, PromQL, scrape, observability, MeterBinder, FunctionCounter.
---

# Observability (dev machine)

Prometheus scrapes the three services and Grafana draws them. Both run in Docker; the services run on the host (IDE).
Nothing here is for a deployment yet: Grafana lets anyone on the machine in as admin.

## Start it

```bash
docker compose -f zone-server/compose.yaml --profile observability up -d prometheus grafana
```

They sit in a compose *profile* so that `bootRun`'s start-and-stop of `zone-server/compose.yaml` leaves them alone and
they survive a zone restart. `docker compose ... down` without `--profile observability` does not stop them either.
The compose project is named after the folder (`zone-server`), so a worktree and the main checkout share containers and
volumes: starting the stack from one re-mounts the config of that checkout.

| What | URL |
|---|---|
| Grafana (anonymous admin) | `http://127.0.0.1:3000` - start at `/d/bestia-overview` |
| Prometheus | `http://127.0.0.1:9090` |
| Zone scrape | `http://127.0.0.1:8092/metrics` (bound to all interfaces under `dev`, so the container can reach it) |
| login-server scrape | `:8093/actuator/prometheus` (dev profile, separate management port) |
| api-service scrape | `:8094/actuator/prometheus` (dev profile, separate management port) |

Docker Desktop must be running. Prometheus reaches the host through `host.docker.internal`; a target that is `down` with
"connection refused" just means that service is not running.

## Query it from a shell

```bash
# Are the targets up?
curl -s http://127.0.0.1:9090/api/v1/targets | node -e "const t=JSON.parse(require('fs').readFileSync(0));for(const x of t.data.activeTargets)console.log(x.labels.job,x.health,x.lastError)"
# Instant query (URL-encode the PromQL)
curl -s -G http://127.0.0.1:9090/api/v1/query --data-urlencode 'query=sum(rate(zone_tick_overruns_total[1m]))'
# Range query
curl -s -G http://127.0.0.1:9090/api/v1/query_range --data-urlencode 'query=max(zone_inbox_pending)' --data-urlencode "start=$(date -d '-10 min' +%s)" --data-urlencode "end=$(date +%s)" --data-urlencode 'step=15'
# What series exist for a prefix?
curl -s -G http://127.0.0.1:9090/api/v1/label/__name__/values | node -e "console.log(JSON.parse(require('fs').readFileSync(0)).data.filter(n=>n.startsWith('zone_')).join('\n'))"
# Grafana: dashboards, and one dashboard's JSON
curl -s 'http://127.0.0.1:3000/api/search?type=dash-db'
curl -s http://127.0.0.1:3000/api/dashboards/uid/bestia-overview
```

Without a running Prometheus, the zone's own scrape is the same data: `curl -s http://127.0.0.1:8092/metrics`.
To *look* at a dashboard, open `http://127.0.0.1:3000/d/<uid>` in the in-app browser and use `read_page` / a screenshot
(`?from=now-15m&to=now&kiosk` for a bare view).

## Naming: Micrometer -> Prometheus

Dots become underscores; the unit and the type add suffixes.

| Micrometer | Prometheus |
|---|---|
| `Counter zone.tick.overruns` | `zone_tick_overruns_total` |
| `Gauge zone.entities` | `zone_entities` |
| `Timer zone.tick.duration{part=total}` | `zone_tick_duration_seconds_count / _sum / _max` (+ `_bucket{le}` when it has SLO buckets) |
| `Counter zone.socket.written` (baseUnit bytes) | `zone_socket_written_bytes_total` |
| `DistributionSummary zone.socket.flush.size` (bytes) | `zone_socket_flush_size_bytes_bucket / _count / _sum` |

Every series also carries `application` and `shard` (zone) from `management.metrics.tags`, and `job`/`instance` from the scrape.

## Where the zone's meters come from

`zone-server/.../metrics/`: `ZoneMeters` (queues and counts, read when scraped), `TickMetrics` (the tick's own timers),
`SystemMeters` (per-system cost, bound on `ApplicationStartedEvent`), plus `socket/SocketTraffic`. The convention: the
owning class keeps a plain counter or `@Volatile` field, a `MeterBinder` reads it at scrape time, and the tick pays for no
meter. Anything the tick thread owns (the ECS, `HashMap`s in systems) is never read from the scrape thread: publish a
volatile snapshot from the tick instead. Tags are bounded enums only, never account or entity ids.

## Dashboards

`observability/grafana/dashboards/*.json`, provisioned into the folder "Bestia" (`allowUiUpdates: false`: the files
are the source of truth, Grafana re-reads them within ~10 s, a UI edit cannot be saved back). Overview and JVM & Process
cover every service; the rest are per service. `DashboardMetricsTest` (zone-server) fails when a dashboard queries a
`zone_*` series the booted zone does not export; the other modules check their own prefix.

## PromQL for the questions that come up

```promql
# Is the tick thread keeping up? 1.0 = never sleeps
sum(rate(zone_tick_duration_seconds_sum{part="total"}[1m]))
# World time lost to dropped steps (steps/s x tick period)
rate(zone_tick_dropped_steps_total[1m])
# What eats the tick?
topk(5, sum by (system) (rate(zone_system_duration_seconds_sum[1m])))
```

## Adding a metric to the zone

- **Where the class goes.** `FeatureSliceRulesTest` pins the dependency tiers, and `metrics` sits near the top (only
  `engine` and `boot` are above it). A feature that records something owns its own small class (`MessageMetrics`
  in `message`, `ChunkStreamMetrics` in `world.stream`, `NavigationMetrics`, `AiMetrics`, `PersistenceMetrics`,
  `AuthMetrics`; `GameMetrics` in `entity` for the ones several features share). `metrics/*Meters` binders read
  what a source keeps; they may depend downwards, nobody may depend on them. Something owned by `engine` registers
  itself (`TickMetrics.anomalySource`), because `metrics` cannot import `engine`.
- **Constructor param with a default.** `metrics: XMetrics = XMetrics()` last, backed by a `SimpleMeterRegistry`,
  so the tests that build the class by hand keep compiling. Do not change the return type of a method many tests
  stub with mockk (`AsyncJobExecutor.submit` has ~20): add a sibling (`trySubmit`).
- **Tags are bounded** (enums, handler classes, goal names), never an account or entity id. Series made on first
  use (a tag from a list nobody enumerates) must be named in `DashboardMetricsTest.LAZY_SERIES` and pinned by a test
  of their own, or the dashboard test cannot see them.
- **A new `System`** changes `src/test/resources/architecture/tick-order.txt`: add its line (wave, name, schedule).
- **A meter name with a tag it did not have must not reuse an old untagged name**: Prometheus needs every series of
  one name to carry the same tag keys (`zone.entities` is untagged, so the per-kind gauge is `zone.world.entities`).

## Dashboards: checking them against a running zone

`DashboardMetricsTest` only proves the names exist. To prove the queries run, with the zone up, run every panel
expression against `/api/v1/query` (replace `$__rate_interval` with `30s`, `$shard` with `.*`) and look for errors
and empty results. Known traps: `sum by (x) (...) / sum(...)` has no matching labels and comes back empty - use
`/ scalar(sum(...))`; the tick histogram's `le` values are `0.025`, `0.05`, `0.1` at 20 Hz;
`process_files_open_files` does not exist on Windows. A panel that is empty with no players is normal: counters
that have not moved, and the series that only appear on first use.
