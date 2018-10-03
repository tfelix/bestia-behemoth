package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.PongMessage
import net.bestia.messages.login.LoginAuthRequestMessage
import net.bestia.messages.login.LoginAuthResponseMessage
import net.bestia.messages.login.LoginResponse
import net.bestia.zoneserver.client.AuthenticationService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.*

private val LOG = KotlinLogging.logger { }

class LatencyProvider {
  private val buffer = LinkedList<Int>()

  fun addLatency(ms: Int) {
    if (buffer.size > LATENCY_BUFFER_SIZE) {
      buffer.pop()
    }
    buffer.push(ms)
  }

  fun getLatency(): Int {
    return buffer.sum() / LATENCY_BUFFER_SIZE
  }

  companion object {
    private const val LATENCY_BUFFER_SIZE = 5
  }
}

@Component
@Scope("prototype")
class WebsocketActor(
    private val authService: AuthenticationService
) : AbstractActor() {

  private lateinit var clientSocketActor: ActorRef
  private val latencyProvider = LatencyProvider()

  @Suppress("LeakingThis")
  private val authenticated = receiveBuilder()
      .matchAny(this::processAnyMessage)
      .build()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ActorRef::class.java, this::onInit)
        .match(LoginAuthRequestMessage::class.java, this::onAuth)
        .matchAny { context.stop(self) }
        .build()
  }

  private fun onAuth(authRequestMsg: LoginAuthRequestMessage) {
    if (!::clientSocketActor.isInitialized) {
      LOG.error { "Received auth message before ref to client was initialized" }
      context.stop(self)
      return
    }

    val respMessage = if (authService.isUserAuthenticated(authRequestMsg.token)) {
      context.become(authenticated)
      LoginAuthResponseMessage(LoginResponse.SUCCESS)
    } else {
      LoginAuthResponseMessage(LoginResponse.DENIED)
    }

    clientSocketActor.tell(respMessage, self)
  }

  private fun onInit(outActor: ActorRef) {
    this.clientSocketActor = outActor
  }

  // TODO Send Latency to the messages who need it.
  private fun processAnyMessage(msg: Any) {
    when (msg) {
      INIT -> {
        println("Init")
        sender.tell(ACK, self)
      }
      COMPLETE -> {
        println("Stream complete")
      }
      is PongMessage -> handleLatencyPong(msg)
      else -> {
        println("Received: $msg")
        sender.tell(ACK, self)
      }
    }
    sender.tell(ACK, self)
  }

  private fun handleLatencyPong(msg: PongMessage) {
    val latency = System.currentTimeMillis() - msg.start
    latencyProvider.addLatency(latency.toInt())
  }

  companion object {
    const val ACK = "onAck"
    const val INIT = "onInit"
    const val COMPLETE = "onComplete"
  }
}

/*
  override fun createReceive(builder: BuilderFacade) {
    builder.matchEquals(LATENCY_REQUEST_MSG) { onLatencyRequest() }
  }

  private val latencyTick = context.system.scheduler().schedule(
      Duration.create(2, TimeUnit.SECONDS),
      Duration.create(5, TimeUnit.SECONDS),
      self, LATENCY_REQUEST_MSG, context.dispatcher(), null)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder().build()
  }

  @Throws(Exception::class)
  override fun preStart() {
    val now = System.currentTimeMillis()
    latencyService.addLatency(accountId, now, now + 10)
  }

  @Throws(Exception::class)
  override fun postStop() {
    latencyTick.cancel()
    latencyService.delete(accountId)
  }

  /**
   * The client is send a latency request message.
   */
  private fun onLatencyRequest() {

    // Check how many latency requests we have missed.
    val lastReply = latencyService.getLastClientReply(accountId)
    val dLastReply = System.currentTimeMillis() - lastReply

    if (lastReply > 0 && dLastReply > CLIENT_TIMEOUT_MS) {
      // Connection seems to have dropped. Signal the server that the
      // client has disconnected and terminate.
      context.parent().tell(PoisonPill.getInstance(), self)
    } else {
      val ping = PingMessage(accountId)
      clientConnection.tell(ping, self)
    }
  }

  companion object {
    const val NAME = "ping"
    private const val LATENCY_REQUEST_MSG = "latency"
    private const val CLIENT_TIMEOUT_MS = 30 * 1000
  }
 */
