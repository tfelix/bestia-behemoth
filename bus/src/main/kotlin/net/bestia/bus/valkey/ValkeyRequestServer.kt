package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import net.bestia.bus.BusFrame
import net.bestia.bus.BusKeys
import net.bestia.bus.RequestHandler
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A zone node's end of request/reply: takes requests off its channel and answers them, at most [maxInFlight]
 * at a time. One over the limit is answered [BusReply.Status.BUSY] at once rather than queued, so a flood
 * cannot build a backlog the zone would still be working through after every caller has given up.
 *
 * A request with an idempotency key stores its reply for [idempotencySeconds]; a repeat gets that reply instead
 * of running again.
 */
class ValkeyRequestServer(
  client: RedisClient,
  nodeId: String,
  private val handler: RequestHandler,
  private val maxInFlight: Int = 64,
  private val idempotencySeconds: Long = 600,
) : AutoCloseable {

  private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
  private val inFlight = Semaphore(maxInFlight)
  private val workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("bus-request-", 0).factory())
  private val rejected = AtomicLong()

  /** What this server has done, for whoever exports metrics; `bus` itself depends on no metrics library. */
  val stats = RequestServerStats()

  private val commands = client.connect(codec)
  private val subscriber = client.connectPubSub(codec).apply {
    addListener(object : RedisPubSubAdapter<String, ByteArray>() {
      override fun message(channel: String, message: ByteArray) {
        onRequest(message)
      }
    })
    sync().subscribe(BusKeys.requests(nodeId))
  }

  val inFlightCount: Int
    get() {
      return maxInFlight - inFlight.availablePermits()
    }

  /** Requests answered busy since start, because [maxInFlight] were already running. */
  val rejectedCount: Long
    get() {
      return rejected.get()
    }

  private fun onRequest(message: ByteArray) {
    stats.received.increment()

    val request = try {
      BusFrame.decodeRequest(message)
    } catch (e: Exception) {
      stats.unreadable.increment()
      LOG.warn(e) { "Dropped an unreadable request" }
      return
    }

    if (!inFlight.tryAcquire()) {
      rejected.incrementAndGet()
      send(request, BusReply.of(request.correlationId, BusReply.Status.BUSY))
      return
    }

    workers.execute {
      try {
        if (System.currentTimeMillis() < request.deadlineEpochMs) {
          send(request, answer(request))
        } else {
          // The caller has given up, so nobody is waiting for an answer.
          stats.expired.increment()
        }
      } finally {
        inFlight.release()
      }
    }
  }

  private fun answer(request: BusRequest): BusReply {
    val key = request.idempotencyKey?.let { BusKeys.idempotency(request.accountId, it) }
    if (key != null) {
      val earlier = commands.sync().get(key)
      if (earlier != null) {
        stats.replayed.increment()
        val stored = BusFrame.decodeReply(earlier)
        return BusReply(request.correlationId, stored.status, stored.answer, stored.detail, stored.body)
      }
    }

    val type = stats.of(request.query::class.simpleName ?: "unknown")
    val started = System.nanoTime()
    val reply = try {
      handler.handle(request)
    } catch (e: Exception) {
      stats.failed.increment()
      LOG.error(e) { "Request ${request.query::class.simpleName} for account ${request.accountId} failed" }
      return BusReply.of(request.correlationId, BusReply.Status.BUSY, "internal error")
    } finally {
      type.record(System.nanoTime() - started)
    }

    if (key != null && reply.status != BusReply.Status.BUSY) {
      commands.sync().set(key, BusFrame.encode(reply), SetArgs.Builder.ex(idempotencySeconds))
    }
    return reply
  }

  private fun send(request: BusRequest, reply: BusReply) {
    commands.async().publish(request.replyTo, BusFrame.encode(reply))
  }

  override fun close() {
    subscriber.close()
    workers.shutdown()
    workers.awaitTermination(5, TimeUnit.SECONDS)
    commands.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
