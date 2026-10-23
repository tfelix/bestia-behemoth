package net.bestia.zone.world.stream

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * What terrain streaming spends and sends. Streaming is the most expensive thing the tick does, and every one of
 * its budgets is per tick, so a tick that is late quietly divides the whole layer's throughput. These meters say
 * which budget ran out, and which step of [ChunkStreamSystem] the time went to.
 *
 * Recorded on the tick thread, except the worker timers and the request-side drops, which are other threads'.
 * Micrometer's counters and timers are safe for both. The default registry serves tests that build the parts
 * by hand.
 */
@Component
class ChunkStreamMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** The steps of [ChunkStreamSystem.update], in the order they run. */
  enum class Step {
    WORKERS, CARVES, TELEPORTS, SUBSCRIPTIONS, BROADCAST, REQUESTS, DERIVED, REGROUND
  }

  /** Every per-tick budget there is, so "which one ran out" is a single question. */
  enum class Allowance {
    /** [ChunkStreamConfig.slabComputationsPerTick]: costing a column of the view. */
    SLAB,

    /** [ChunkStreamConfig.derivedRebuildsPerTick]: rebuilding walkability and ground height. */
    DERIVED,

    /** [ChunkStreamConfig.encodesPerTick]: generating and encoding a chunk off the tick. */
    ENCODE,

    /** [ChunkStreamConfig.requestBurst]: a client asking for more chunks than it is served. */
    REQUEST_TOKENS,

    /** [ChunkStreamConfig.chunksPerTickPerPlayer]: one connection's share of the tick's sends. */
    PER_PLAYER,
  }

  /** What was sent: a chunk the client asked for, the one under their feet, a patch, or a snapshot instead of one. */
  enum class Kind {
    FULL, GROUND, PATCH, SNAPSHOT
  }

  private val stepTimers = Step.entries.associateWith { step ->
    Timer.builder("zone.chunk.step.duration")
      .tag("step", step.name.lowercase())
      .description("Time one step of the chunk stream system took per tick")
      .register(registry)
  }

  private val spent = Allowance.entries.associateWith { budget ->
    Counter.builder("zone.chunk.budget.spent")
      .tag("budget", budget.name.lowercase())
      .description("What the per-tick budgets paid for: slab computations, derived rebuilds, encodes, request tokens")
      .register(registry)
  }

  private val exhausted = Allowance.entries.associateWith { budget ->
    Counter.builder("zone.chunk.budget.exhausted")
      .tag("budget", budget.name.lowercase())
      .description("Ticks in which a budget ran out with work still waiting; that work goes on a later tick")
      .register(registry)
  }

  private val sent = Kind.entries.associateWith { chunkCounter("zone.chunk.sent", it, "Chunk messages queued to clients") }
  private val sentBytes = Kind.entries.associateWith { chunkCounter("zone.chunk.sent.bytes", it, "Payload bytes of those messages") }

  private val sendRefused = Counter.builder("zone.chunk.send.refused")
    .description("Chunks a client's connection would not take (gone or full); they stay queued and are tried again")
    .register(registry)

  private val manifests = listOf("sent", "refused").associateWith { result ->
    Counter.builder("zone.chunk.manifests")
      .tag("result", result)
      .description("Manifests telling a client which chunks it may ask for")
      .register(registry)
  }

  private val manifestChunks = listOf("added", "removed").associateWith { change ->
    Counter.builder("zone.chunk.manifest.chunks")
      .tag("change", change)
      .description("Chunks the manifests offered and withdrew")
      .register(registry)
  }

  private val requests = listOf("queued", "deferred", "unannounced").associateWith { outcome ->
    Counter.builder("zone.chunk.requests")
      .tag("outcome", outcome)
      .description("Chunks clients asked for: queued to send, deferred for lack of tokens, or never offered to them")
      .register(registry)
  }

  private val carves = listOf("applied", "rejected").associateWith { result ->
    Counter.builder("zone.chunk.carves")
      .tag("result", result)
      .description("Carve requests; rejected ones asked for a brush smaller than the client can draw")
      .register(registry)
  }

  private val carvedVoxels = Counter.builder("zone.chunk.carve.voxels")
    .description("Voxels carves removed")
    .register(registry)

  private val carvedChunks = Counter.builder("zone.chunk.carve.chunks")
    .description("Chunks carves touched, counted once per carve")
    .register(registry)

  private val slabsComputed = Counter.builder("zone.chunk.slabs.computed")
    .description("Columns whose surface slabs were costed; the expensive thing the slab budget limits")
    .register(registry)

  private val encodedCache = listOf("hit", "miss").associateWith { result ->
    Counter.builder("zone.chunk.encoded.cache")
      .tag("result", result)
      .description("Lookups of an encoded chunk payload at its current revision")
      .register(registry)
  }

  private val workerTimers = listOf("generate", "encode").associateWith { kind ->
    Timer.builder("zone.chunk.worker.duration")
      .tag("kind", kind)
      .description("Time a chunk worker took: generate and encode a new chunk, or encode one already generated")
      .register(registry)
  }

  private val journalFlushed = Counter.builder("zone.chunk.journal.flushed")
    .description("Edited chunks handed to the database")
    .register(registry)

  private val unsettled = AtomicInteger()
  private val sendQueue = AtomicInteger()
  private val carvedPending = AtomicInteger()
  private val journalPending = AtomicInteger()

  /** When the oldest unsaved edit was made, in [System.nanoTime], or 0 when none waits. */
  private val journalSince = AtomicLong()

  init {
    Gauge.builder("zone.chunk.manifests.unsettled", unsettled) { it.get().toDouble() }
      .description("Players whose view is not fully offered yet, because a budget ran out or a manifest was refused")
      .register(registry)
    Gauge.builder("zone.chunk.send.queue", sendQueue) { it.get().toDouble() }
      .description("Chunks asked for and not yet sent, over every player")
      .register(registry)
    Gauge.builder("zone.chunk.carved.pending", carvedPending) { it.get().toDouble() }
      .description("Chunks a carve touched whose occupants have not been put back on the ground")
      .register(registry)
    Gauge.builder("zone.chunk.journal.pending", journalPending) { it.get().toDouble() }
      .description("Edited chunks waiting to be written to the database")
      .register(registry)
    Gauge.builder("zone.chunk.journal.oldest.age", journalSince) { age(it.get()) }
      .baseUnit("seconds")
      .description("How long the oldest edit not yet handed to the database has been waiting; 0 when none is")
      .register(registry)
  }

  fun <T> timed(step: Step, block: () -> T): T {
    val started = System.nanoTime()
    try {
      return block()
    } finally {
      stepTimers.getValue(step).record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
    }
  }

  fun spent(budget: Allowance, amount: Int) {
    if (amount > 0) spent.getValue(budget).increment(amount.toDouble())
  }

  fun exhausted(budget: Allowance) {
    exhausted.getValue(budget).increment()
  }

  /** [messages] chunk messages of [kind] and [bytes] payload bytes in all were queued. */
  fun sent(kind: Kind, messages: Int, bytes: Long) {
    sent.getValue(kind).increment(messages.toDouble())
    sentBytes.getValue(kind).increment(bytes.toDouble())
  }

  fun sendRefused() {
    sendRefused.increment()
  }

  fun manifest(sent: Boolean, added: Int = 0, removed: Int = 0) {
    manifests.getValue(if (sent) "sent" else "refused").increment()
    if (!sent) return

    manifestChunks.getValue("added").increment(added.toDouble())
    manifestChunks.getValue("removed").increment(removed.toDouble())
  }

  fun requests(queued: Int = 0, deferred: Int = 0, unannounced: Int = 0) {
    requests.getValue("queued").increment(queued.toDouble())
    requests.getValue("deferred").increment(deferred.toDouble())
    requests.getValue("unannounced").increment(unannounced.toDouble())
  }

  fun carveRejected() {
    carves.getValue("rejected").increment()
  }

  fun carved(voxels: Int, chunks: Int) {
    carves.getValue("applied").increment()
    carvedVoxels.increment(voxels.toDouble())
    carvedChunks.increment(chunks.toDouble())
  }

  fun slabComputed() {
    slabsComputed.increment()
  }

  fun encodedCache(hit: Boolean) {
    encodedCache.getValue(if (hit) "hit" else "miss").increment()
  }

  /** Times a chunk worker's job; [generated] is false when the chunk's voxels were already in hand. */
  fun <T> worked(generated: Boolean, block: () -> T): T {
    val started = System.nanoTime()
    try {
      return block()
    } finally {
      workerTimers.getValue(if (generated) "generate" else "encode").record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
    }
  }

  fun journalFlushed(chunks: Int) {
    journalFlushed.increment(chunks.toDouble())
  }

  fun unsettled(players: Int) {
    unsettled.set(players)
  }

  fun sendQueue(chunks: Int) {
    sendQueue.set(chunks)
  }

  fun carvedPending(chunks: Int) {
    carvedPending.set(chunks)
  }

  /** The edit journal now holds [pending] chunks; the oldest of them has waited since [since] ([System.nanoTime]). */
  fun journal(pending: Int, since: Long) {
    journalPending.set(pending)
    journalSince.set(if (pending == 0) 0L else since)
  }

  private fun age(since: Long): Double {
    return if (since == 0L) 0.0 else (System.nanoTime() - since) / 1_000_000_000.0
  }

  private fun chunkCounter(name: String, kind: Kind, description: String): Counter {
    return Counter.builder(name)
      .tag("kind", kind.name.lowercase())
      .description(description)
      .register(registry)
  }
}
