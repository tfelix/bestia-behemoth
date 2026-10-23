package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import net.bestia.bus.BusFrame
import net.bestia.bus.BusKeys
import net.bestia.bus.Directory
import net.bestia.bus.NodeBus
import net.bestia.bus.ZoneUnavailableException
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneQuery
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.LongAdder

/**
 * Request/reply over pub/sub. A lost message costs a timeout, never a wrong answer: replies are matched by
 * correlation id, and one that arrives after its caller gave up finds nothing waiting and is dropped.
 *
 * @param defaultNode where a request goes for an account the [directory] has no entry for, i.e. one that is not
 *   in the world. One node holds everything today; with several, this becomes the realm's home node.
 */
class ValkeyNodeBus(
  client: RedisClient,
  private val directory: Directory,
  private val defaultNode: String,
  instanceId: String,
) : NodeBus, AutoCloseable {

  private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
  private val replyChannel = BusKeys.replies(instanceId)
  private val pending = ConcurrentHashMap<String, CompletableFuture<BusReply>>()

  private val asks = LongAdder()
  private val timeouts = LongAdder()
  private val unavailable = LongAdder()
  private val publishErrors = LongAdder()
  private val unreadableReplies = LongAdder()
  private val orphanReplies = LongAdder()

  /** Requests sent since start; what [timeoutCount] and the rest are a share of. For a metrics binder to read. */
  val askCount: Long get() = asks.sum()

  /** Requests whose caller gave up before a reply came. */
  val timeoutCount: Long get() = timeouts.sum()

  /** Requests no zone was listening for. */
  val unavailableCount: Long get() = unavailable.sum()

  /** Requests the publish itself failed for, a broken connection say. */
  val publishErrorCount: Long get() = publishErrors.sum()

  /** Replies that arrived on this instance's channel and could not be decoded. */
  val unreadableReplyCount: Long get() = unreadableReplies.sum()

  /** Replies nobody was waiting for any more: the caller timed out first, or the reply was not meant for it. */
  val orphanReplyCount: Long get() = orphanReplies.sum()

  /** Requests sent and not yet answered. */
  val pendingCount: Int get() = pending.size

  private val publisher = client.connect(codec)
  private val subscriber = client.connectPubSub(codec).apply {
    addListener(object : RedisPubSubAdapter<String, ByteArray>() {
      override fun message(channel: String, message: ByteArray) {
        onReply(message)
      }
    })
    sync().subscribe(replyChannel)
  }

  override fun ask(
    accountId: Long,
    query: ZoneQuery,
    idempotencyKey: String?,
    timeout: Duration,
  ): CompletableFuture<BusReply> {
    val node = directory.nodeOf(accountId) ?: defaultNode
    val request = BusRequest(
      correlationId = UUID.randomUUID().toString(),
      replyTo = replyChannel,
      deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis(),
      accountId = accountId,
      idempotencyKey = idempotencyKey,
      query = query,
    )

    val reply = CompletableFuture<BusReply>()
    pending[request.correlationId] = reply
    asks.increment()
    reply.whenComplete { _, error ->
      pending.remove(request.correlationId)
      if (error is TimeoutException) {
        timeouts.increment()
      }
    }
    reply.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)

    publisher.async().publish(BusKeys.requests(node), BusFrame.encode(request)).whenComplete { receivers, error ->
      when {
        error != null -> {
          publishErrors.increment()
          reply.completeExceptionally(error)
        }

        receivers == 0L -> {
          unavailable.increment()
          reply.completeExceptionally(ZoneUnavailableException(node))
        }
      }
    }

    return reply
  }

  private fun onReply(message: ByteArray) {
    val reply = try {
      BusFrame.decodeReply(message)
    } catch (e: Exception) {
      unreadableReplies.increment()
      LOG.warn(e) { "Dropped an unreadable reply on $replyChannel" }
      return
    }

    val waiting = pending[reply.correlationId]
    if (waiting == null) {
      orphanReplies.increment()
      return
    }
    waiting.complete(reply)
  }

  override fun close() {
    subscriber.close()
    publisher.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
