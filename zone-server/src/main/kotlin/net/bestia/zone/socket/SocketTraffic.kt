package net.bestia.zone.socket

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.account.KickReason
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.BnetMessageProcessorAdapter
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * What all game connections together read, write and flush, and why they end. No per-account tags: one series per
 * player would outgrow the metrics store. The default registry serves tests that build a pipeline by hand.
 */
@Component
class SocketTraffic(registry: MeterRegistry = SimpleMeterRegistry()) {

  private val written = Counter.builder("zone.socket.written")
    .baseUnit("bytes")
    .description("Bytes flushed to game clients")
    .register(registry)

  private val read = Counter.builder("zone.socket.read")
    .baseUnit("bytes")
    .description("Bytes received from game clients")
    .register(registry)

  private val flushes = Counter.builder("zone.socket.flushes")
    .description("Flushes to game clients; each is at least one write syscall")
    .register(registry)

  private val flushSize = DistributionSummary.builder("zone.socket.flush.size")
    .baseUnit("bytes")
    .description("Bytes per flush")
    .serviceLevelObjectives(1024.0, 4096.0, 16384.0, 65536.0, 262144.0)
    .register(registry)

  private val droppedForBacklog = dropCounter(registry, "backlog")
  private val droppedAsUnwritable = dropCounter(registry, "unwritable")

  private val refusedPerAddress = Counter.builder("zone.socket.refused")
    .tag("reason", "per_address")
    .description("Connections refused before they were read from")
    .register(registry)

  private val loggedIn = Counter.builder("zone.login.sessions")
    .tag("event", "started")
    .description("Logins the handshake let through, and the ones that took an account over from an older connection")
    .register(registry)

  private val takeovers = Counter.builder("zone.login.sessions")
    .tag("event", "takeover")
    .register(registry)

  private val ignoredAfterClose = ignoredCounter(registry, "closing")
  private val ignoredAfterTakeover = ignoredCounter(registry, "displaced")

  /** Indexed by [MessageCase.ordinal]; null for a case the server never sends. */
  private val sentByCase = arrayOfNulls<Counter>(MessageCase.values().size)
  private val bytesByCase = arrayOfNulls<Counter>(MessageCase.values().size)

  private val disconnects: Map<String, Counter>

  private val open = AtomicInteger()

  init {
    val inbound = BnetMessageProcessorAdapter.inboundCases()
    for (case in MessageCase.values()) {
      if (case == MessageCase.MESSAGE_NOT_SET || case == MessageCase.AUTHENTICATION || case in inbound) continue

      sentByCase[case.ordinal] = Counter.builder("zone.smsg.sent")
        .tag("type", case.name)
        .description("Messages queued to game clients, one per message and receiver; entity state is STATE_BATCH")
        .register(registry)
      bytesByCase[case.ordinal] = Counter.builder("zone.smsg.bytes")
        .tag("type", case.name)
        .baseUnit("bytes")
        .description("Framed bytes of those messages, before compression or TCP overhead")
        .register(registry)
    }

    disconnects = (DISCONNECT_REASONS + OTHER_REASON).associateWith { reason ->
      Counter.builder("zone.socket.disconnects")
        .tag("reason", reason)
        .description("Connections the zone ended on purpose, by why")
        .register(registry)
    }

    Gauge.builder("zone.socket.channels", open) { it.get().toDouble() }
      .description("Open game connections, authenticated or not")
      .register(registry)
  }

  fun opened() {
    open.incrementAndGet()
  }

  fun closed() {
    open.decrementAndGet()
  }

  fun read(bytes: Int) {
    read.increment(bytes.toDouble())
  }

  fun flushed(bytes: Long) {
    flushes.increment()
    if (bytes == 0L) return

    written.increment(bytes.toDouble())
    flushSize.record(bytes.toDouble())
  }

  fun droppedForBacklog() {
    droppedForBacklog.increment()
  }

  fun droppedAsUnwritable() {
    droppedAsUnwritable.increment()
  }

  /** A message of [case] and [frameBytes] (length prefix included) was queued to one client. */
  fun sent(case: MessageCase, frameBytes: Int) {
    sentByCase[case.ordinal]?.increment()
    bytesByCase[case.ordinal]?.increment(frameBytes.toDouble())
  }

  /** The handshake accepted a login. */
  fun loggedIn() {
    loggedIn.increment()
  }

  /** A login took the account over from a connection it already had. */
  fun tookOver() {
    takeovers.increment()
  }

  /** A connection was refused because its address already held the most it may. */
  fun refusedPerAddress() {
    refusedPerAddress.increment()
  }

  /** A message arrived on a connection that must no longer act: it is closing, or a newer login took over. */
  fun ignoredAfterClose() {
    ignoredAfterClose.increment()
  }

  fun ignoredAfterTakeover() {
    ignoredAfterTakeover.increment()
  }

  /** The zone told a client [reason] and closed its connection. */
  fun disconnected(reason: String) {
    disconnects.getValue(reasonOf(reason)).increment()
  }

  private fun dropCounter(registry: MeterRegistry, reason: String): Counter {
    return Counter.builder("zone.socket.dropped")
      .tag("reason", reason)
      .description("Clients closed because they stopped reading")
      .register(registry)
  }

  private fun ignoredCounter(registry: MeterRegistry, reason: String): Counter {
    return Counter.builder("zone.socket.ignored")
      .tag("reason", reason)
      .description("Messages that arrived on a connection that may no longer act")
      .register(registry)
  }

  companion object {
    private const val OTHER_REASON = "OTHER"

    /**
     * What a disconnect reason is called as a tag. The zone's own reasons, and the login server's kick reasons,
     * which arrive as given. Anything else, and the error code after the colon of an internal error, is folded
     * away so a reason can never mint a series.
     */
    private val DISCONNECT_REASONS = listOf(
      "AUTH_FAILED", "RATE_LIMITED", "IDLE_TIMEOUT", "UNKNOWN_MESSAGE", "INTERNAL_SERVER_ERROR", "INBOX_OVERFLOW",
      "OTHER_CONNECTION", "PROTOCOL_MISMATCH", "SERVER_NOT_READY",
    ) + KickReason.entries.map { it.name }

    private fun reasonOf(reason: String): String {
      val head = reason.substringBefore(':')

      return if (head in DISCONNECT_REASONS) head else OTHER_REASON
    }
  }
}
