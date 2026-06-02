# AutoArchive System

Automated storage lifecycle platform: scan files, apply retention policies, archive inactive files, and write audit logs (file + PostgreSQL).

**Stack:** Java 21, Spring Boot, PostgreSQL, Flyway, Virtual Threads

---

## Performance (local benchmark)

Tested on Windows with ~2.5k–2.9k files in `data/active` (local disk).

| Mode | Files | Move | Audit flush | **Total** |
|------|------:|-----:|------------:|----------:|
| **Parallel** | 2902 | 1586 ms | 691 ms | **2282 ms** |
| **Sequential** | 2539 | 2315 ms | 220 ms | **2536 ms** |

| Metric | Before optimization (parallel) | After optimization (parallel) |
|--------|-------------------------------:|--------------------------------:|
| Move phase | ~23,238 ms | **1,586 ms** |
| Total time | ~23,698 ms | **2,282 ms** |

> Parallel mode is ~10% faster end-to-end and ~31% faster on the move phase vs sequential (same-machine test).

---

## How we achieved parallel optimization

| Problem | Solution |
|---------|----------|
| 500+ tasks / `Future`s created per run | Fixed **worker pool** (e.g. 8 workers) with shared atomic work index |
| All files moved into one `archive/` folder | **Sharded folders** `archive/shard-0` … `archive/shard-7` (one per worker) to avoid Windows directory lock contention |
| Per-file DB `INSERT` blocking parallel work | **Async batched DB writer** (queue + batch `INSERT`, default 100 rows) |
| 500 threads appending one `audit.log` | **Async batched file writer** (single background thread, batch append) |
| Audit work during moves | **Deferred audit**: moves finish first, then audit is enqueued in bulk |

---

## Where virtual threads are used

| Location | Class | What threads do |
|----------|-------|-----------------|
| **File scan** | `LocalFileStorageService` | One virtual thread per file path to read metadata (`size`, `lastModified`) in parallel |
| **Parallel archive** | `ArchivePipelineService` | **8 worker virtual threads** (configurable) pull file indices and move files to shard folders |
| **Audit file (background)** | `AsyncAuditFileWriter` | 1 virtual thread drains queue, batches lines, appends to `audit.log` |
| **Audit DB (background)** | `AsyncAuditDbWriter` | 1 virtual thread drains queue, batch-inserts into `audit_logs` |

**Executor:** `VirtualThreadArchiveExecutor` → `Executors.newVirtualThreadPerTaskExecutor()` (Java 21)

---

## Parallel mode: 8 workers — what each does

Configured in `application.yml`:

```yaml
autoarchive:
  archive:
    mode: parallel
    parallel-concurrency: 8   # number of workers = number of shard folders
```

| Worker | Role |
|--------|------|
| Worker 0–7 | Each runs a loop: take next file index (atomic counter), move file to `archive/shard-{0..7}/`, store result for audit |
| Main thread | After all workers finish: enqueue audit entries, wait for batched file/DB flush |
| Scan (before workers) | Virtual threads read metadata for all files under `data/active` |
| Audit writers (background) | 2 virtual threads (file + DB) flush queues in batches |

**Sharding rule:** `shard = fileIndex % 8` → files spread across 8 folders so workers rarely contend on the same directory.

**Sequential mode:** No worker pool; one file at a time → flat `archive/{fileName}`; audit enqueued per file during the loop.

---

## Pipeline flow (parallel)

```
data/active
    │
    ▼  [Virtual threads] Scan metadata
    │
    ▼  Retention policy check (DB rules)
    │
    ▼  [8 virtual-thread workers] Move files → archive/shard-0 .. shard-7
    │
    ▼  [Main thread] Enqueue audit events
    │
    ▼  [Background virtual threads] Batch write audit.log + audit_logs
```

---

## Configuration (important)

| Property | Purpose |
|----------|---------|
| `autoarchive.archive.mode` | `sequential` or `parallel` |
| `autoarchive.archive.parallel-concurrency` | Worker + shard count (default **8**) |
| `autoarchive.audit.db-enabled` | Toggle DB audit (set `false` to benchmark moves only) |
| `autoarchive.audit.file-enabled` | Toggle file audit |
| `autoarchive.audit.db-batch-size` | DB batch size (default 100) |
| `autoarchive.audit.file-batch-size` | File log batch size (default 100) |

---

## Run locally

```bash
docker compose up -d
cd autoarchive-app
mvn spring-boot:run
```

Logs show timing breakdown:

```
Timing breakdown | mode=PARALLEL | move=... ms | audit-enqueue=... ms | audit-flush=... ms | total=... ms
```

---

## Key modules

| Module | Responsibility |
|--------|----------------|
| `LocalFileStorageService` | Parallel scan of `scan-roots` |
| `RetentionPolicyEvaluator` | DB-backed JSONB retention rules |
| `ArchivePipelineService` | Sequential or parallel archive + timing |
| `FileArchiveService` | Flat move (sequential) or sharded move (parallel) |
| `AuditService` | Routes audit to async file + DB writers |
| Flyway | `audit_logs`, `retention_policies` tables |
