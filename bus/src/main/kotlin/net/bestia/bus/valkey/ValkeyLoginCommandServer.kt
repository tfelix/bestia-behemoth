package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import net.bestia.bus.BusJson
import net.bestia.bus.BusKeys
import net.bestia.bus.LoginCommandHandler
import net.bestia.bus.contract.LoginReply
import net.bestia.bus.contract.LoginRequest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The login server's end of [net.bestia.bus.LoginBus]. It subscribes in the background until Valkey answers, so the
 * login server starts and signs players in while Valkey is down; after that, Lettuce resubscribes on its own.
 *
 * Every subscribed login server receives every command. Only the one that claims it acts, so a ban is carried out
 * once however many login servers run.
 */
class ValkeyLoginCommandServer(
  private val client: RedisClient,
  private val handler: LoginCommandHandler,
  private val retryPause: Duration = Duration.ofSeconds(5),
) : AutoCloseable {

  private val workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("bus-login-command-", 0).factory())

  @Volatile
  private var running = true

  @Volatile
  private var commands: StatefulRedisConnection<String, String>? = null

  @Volatile
  private var subscriber: StatefulRedisPubSubConnection<String, String>? = null

  private val subscribing = Thread.ofVirtual().name("bus-login-subscribe").start { subscribeUntilConnected() }

  val isListening: Boolean
    get() {
      return subscriber != null
    }

  private fun subscribeUntilConnected() {
    while (running) {
      try {
        subscribe()
        return
      } catch (e: Exception) {
        LOG.warn { "Cannot listen for login commands yet (${e.message}); retrying in $retryPause" }
      }

      try {
        Thread.sleep(retryPause)
      } catch (_: InterruptedException) {
        return
      }
    }
  }

  private fun subscribe() {
    val connection = client.connect()
    var pubSub: StatefulRedisPubSubConnection<String, String>? = null
    try {
      pubSub = client.connectPubSub()
      pubSub.addListener(object : RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
          onRequest(message)
        }
      })
      // Before subscribing: the first command may arrive at once, and is answered over this connection.
      commands = connection
      pubSub.sync().subscribe(BusKeys.LOGIN_COMMANDS)
      subscriber = pubSub
    } catch (e: Exception) {
      pubSub?.close()
      commands = null
      connection.close()
      throw e
    }
  }

  private fun onRequest(message: String) {
    val request = try {
      BusJson.mapper.readValue(message, LoginRequest::class.java)
    } catch (e: Exception) {
      LOG.warn(e) { "Dropped an unreadable login command" }
      return
    }

    workers.execute { answer(request) }
  }

  private fun answer(request: LoginRequest) {
    val connection = commands ?: return
    val callerGaveUp = System.currentTimeMillis() >= request.deadlineEpochMs
    if (callerGaveUp || !claim(connection, request)) {
      return
    }

    val outcome = try {
      handler.handle(request.actorAccountId, request.command)
    } catch (e: Exception) {
      LOG.error(e) { "Login command ${request.command} by account ${request.actorAccountId} failed" }
      LoginReply.Outcome.FAILED
    }

    val reply = LoginReply(request.correlationId, outcome)
    connection.async().publish(request.replyTo, BusJson.mapper.writeValueAsString(reply))
  }

  private fun claim(connection: StatefulRedisConnection<String, String>, request: LoginRequest): Boolean {
    val claimArgs = SetArgs.Builder.nx().px(CLAIM_TTL.toMillis())
    return connection.sync().set(BusKeys.loginClaim(request.correlationId), CLAIMED, claimArgs) == "OK"
  }

  override fun close() {
    running = false
    subscribing.interrupt()
    subscribing.join()
    subscriber?.close()
    workers.shutdown()
    workers.awaitTermination(5, TimeUnit.SECONDS)
    commands?.close()
  }

  private companion object {
    const val CLAIMED = "1"

    /** Longer than any caller waits, so a slow second login server finds the claim still there. */
    val CLAIM_TTL: Duration = Duration.ofMinutes(1)

    val LOG = KotlinLogging.logger { }
  }
}
