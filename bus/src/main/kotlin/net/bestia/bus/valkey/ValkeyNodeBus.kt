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
    reply.whenComplete { _, _ -> pending.remove(request.correlationId) }
    reply.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)

    publisher.async().publish(BusKeys.requests(node), BusFrame.encode(request)).whenComplete { receivers, error ->
      when {
        error != null -> reply.completeExceptionally(error)
        receivers == 0L -> reply.completeExceptionally(ZoneUnavailableException(node))
      }
    }

    return reply
  }

  private fun onReply(message: ByteArray) {
    val reply = try {
      BusFrame.decodeReply(message)
    } catch (e: Exception) {
      LOG.warn(e) { "Dropped an unreadable reply on $replyChannel" }
      return
    }

    pending[reply.correlationId]?.complete(reply)
  }

  override fun close() {
    subscriber.close()
    publisher.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
