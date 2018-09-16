package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.ReceiveTimeout
import mu.KotlinLogging
import scala.concurrent.duration.FiniteDuration
import kotlin.reflect.KClass

private val LOG = KotlinLogging.logger { }

data class Responses(
        private val receivedResponses: List<Any>
) {

  fun <T : Any> getReponse(responseClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.first { it::class == responseClass } as T
  }

  fun <T : Any> getAllReponses(responseClass: KClass<T>): List<T> {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.filter { it::class == responseClass }.map { it as T }
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

    if (checkResponseReceived(receivedResponses)) {
      val response = Responses(receivedResponses)
      try {
        action(response)
      } catch (e: Exception) {
        LOG.warn { "Error while executing response action: $e" }
        context.stop(self)
      }
    }
  }

  companion object {
    fun props(
            awaitedResponses: List<KClass<*>>,
            action: (Responses) -> Unit
    ): Props {
      val checkResponseReceived: (List<Any>) -> Boolean = { receivedData ->
        val receivedClasses = receivedData.map { it::class }
        val missing = awaitedResponses - receivedClasses
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