package net.bestia.zoneserver.actor.zone

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.ReceiveTimeout
import mu.KotlinLogging
import scala.concurrent.duration.FiniteDuration

private val LOG = KotlinLogging.logger { }


data class Responses(
        private val receivedResponses: List<Any>
) {

  fun <T> getReponse(responseClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.first { it.javaClass == responseClass } as T
  }

  fun <T> getAllReponses(responseClass: Class<T>): List<T> {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.filter { it.javaClass == responseClass }.map { it as T }
  }
}

class AwaitResponseActor(
        private val action: (Responses) -> Unit,
        private val checkResponseReceived: ((List<Any>) -> Boolean)
) : AbstractActor() {

  private val receivedResponses = mutableListOf<Any>()

  init {
    context.setReceiveTimeout(FiniteDuration.create(5, "seconds"))
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
            .matchAny(this::gatherResponses)
            .build()
  }

  private fun gatherResponses(msg: Any) {
    if (msg is ReceiveTimeout) {
      context.stop(self)
    }

    receivedResponses.add(msg)

    if (hasReceivedAllResponses()) {
      val response = Responses(receivedResponses)
      try {
        action(response)
      } catch (e: Exception) {
        LOG.warn { "Error while executing response action: $e" }
      }
    }

    context.stop(self)
  }

  private fun hasReceivedAllResponses(): Boolean {
    return checkResponseReceived(receivedResponses)
  }

  companion object {
    fun props(
            awaitedResponses: List<Class<*>>,
            action: (Responses) -> Unit
    ): Props {
      val checkResponseReceived: (List<Any>) -> Boolean = {
        val missing = awaitedResponses - it
        missing.isEmpty()
      }
      return Props.create(AwaitResponseActor::class.java) {
        AwaitResponseActor(action, checkResponseReceived)
      }
    }

    fun props(
            checkResponseReceived: ((List<Any>) -> Boolean),
            action: (Responses) -> Unit
    ): Props {
      return Props.create(AwaitResponseActor::class.java) {
        AwaitResponseActor(action, checkResponseReceived)
      }
    }
  }
}