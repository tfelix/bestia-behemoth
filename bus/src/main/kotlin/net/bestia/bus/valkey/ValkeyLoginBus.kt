package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import net.bestia.bus.BusJson
import net.bestia.bus.BusKeys
import net.bestia.bus.LoginBus
import net.bestia.bus.LoginUnavailableException
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply
import net.bestia.bus.contract.LoginRequest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Request/reply with the login server over pub/sub. A lost message costs a timeout, never a wrong answer. */
class ValkeyLoginBus(client: RedisClient, instanceId: String) : LoginBus, AutoCloseable {

  private val replyChannel = BusKeys.loginReplies(instanceId)
  private val pending = ConcurrentHashMap<String, CompletableFuture<LoginReply.Outcome>>()

  private val publisher = client.connect()
  private val subscriber = client.connectPubSub().apply {
    addListener(object : RedisPubSubAdapter<String, String>() {
      override fun message(channel: String, message: String) {
        onReply(message)
      }
    })
    sync().subscribe(replyChannel)
  }

  override fun send(
    actorAccountId: Long,
    command: LoginCommand,
    timeout: Duration,
  ): CompletableFuture<LoginReply.Outcome> {
    val request = LoginRequest(
      correlationId = UUID.randomUUID().toString(),
      replyTo = replyChannel,
      deadlineEpochMs = System.currentTimeMillis() + timeout.toMillis(),
      actorAccountId = actorAccountId,
      command = command,
    )

    val outcome = CompletableFuture<LoginReply.Outcome>()
    pending[request.correlationId] = outcome
    outcome.whenComplete { _, _ -> pending.remove(request.correlationId) }
    outcome.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)

    val json = BusJson.mapper.writeValueAsString(request)
    publisher.async().publish(BusKeys.LOGIN_COMMANDS, json).whenComplete { receivers, error ->
      when {
        error != null -> outcome.completeExceptionally(error)
        receivers == 0L -> outcome.completeExceptionally(LoginUnavailableException())
      }
    }

    return outcome
  }

  private fun onReply(message: String) {
    val reply = try {
      BusJson.mapper.readValue(message, LoginReply::class.java)
    } catch (e: Exception) {
      LOG.warn(e) { "Dropped an unreadable reply on $replyChannel" }
      return
    }

    // Absent when the caller gave up already.
    pending[reply.correlationId]?.complete(reply.outcome)
  }

  override fun close() {
    subscriber.close()
    publisher.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
