package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.ReceiveTimeout
import mu.KotlinLogging
import java.time.Duration
import kotlin.reflect.KClass

private val LOG = KotlinLogging.logger { }

data class Responses(
    val receivedResponses: List<Any>
) {
  fun <T : Any> getResponse(responseClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.first { it::class == responseClass } as T
  }

  inline fun <reified T : Any> getAllResponses(): List<T> {
    return receivedResponses.asSequence().filterIsInstance(T::class.java).toList()
  }
}

/**
 * This Actor will wait until it has received all responses (which is determined via the check responseReceived
 * function) and will then call the responseReceivedCallback. The result of this function is then send to
 * the parent actor who probably has spawned this actor.
 */
class AwaitResponseActor(
    private val responseReceivedCallback: (Responses) -> Any,
    private val checkResponseReceived: ((List<Any>) -> Boolean),
    timeout: Duration
) : AbstractActor() {

  private val receivedResponses = mutableListOf<Any>()

  init {
    context.receiveTimeout = timeout
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .matchAny(this::gatherResponses)
        .build()
  }

  private fun gatherResponses(msg: Any) {
    if (msg is ReceiveTimeout) {
      LOG.debug {
        "Received response timeout in ${context.self().path()}. " +
            "Received so far: ${receivedResponses.map { it.javaClass.simpleName }}"
      }
      context.stop(self)
      return
    }

    receivedResponses.add(msg)

    try {
      if (checkResponseReceived(receivedResponses)) {
        val response = Responses(receivedResponses)
        val responseMsg = responseReceivedCallback(response)
        context.parent.tell(responseMsg, self)
        context.stop(self)
      }
    } catch (e: Exception) {
      LOG.warn(e) { "Error while executing response action in ${context.self().path()}" }
      context.stop(self)
    }
  }

  companion object {
    fun props(
        awaitedResponseTypes: List<KClass<*>>,
        timeout: Duration = Duration.ofMillis(500),
        action: (Responses) -> Any
    ): Props {
      val checkResponseReceived: (List<Any>) -> Boolean = { receivedData ->
        val receivedClasses = receivedData.map { it::class }
        val missing = awaitedResponseTypes - receivedClasses
        missing.isEmpty()
      }

      return Props.create(AwaitResponseActor::class.java) {
        AwaitResponseActor(action, checkResponseReceived, timeout)
      }
    }

    fun props(
        checkResponseReceived: ((List<Any>) -> Boolean),
        timeout: Duration = Duration.ofMillis(500),
        action: (Responses) -> Any
    ): Props {
      return Props.create(AwaitResponseActor::class.java) {
        AwaitResponseActor(action, checkResponseReceived, timeout)
      }
    }
  }
}