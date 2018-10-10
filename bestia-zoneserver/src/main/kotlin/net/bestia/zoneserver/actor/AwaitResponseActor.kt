package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorContext
import akka.actor.Props
import akka.actor.ReceiveTimeout
import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.messages.entity.EntityRequest
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.Entity
import scala.concurrent.duration.FiniteDuration
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass

private val LOG = KotlinLogging.logger { }

class EntitiesResponse(
    private val data: Map<Long, Entity>
) {
  operator fun get(key: Long): Entity {
    return data[key]
        ?: throw IllegalArgumentException("Entity with id $key was not inside response.")
  }

  val all get() = data.values
}

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorContext,
    entityId: Long,
    callback: (Entity) -> Unit
) {
  awaitEntityResponse(messageApi, ctx, setOf(entityId)) {
    it[entityId].let(callback)
  }
}

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorContext,
    entitiyIds: Set<Long>,
    callback: (EntitiesResponse) -> Unit
) {
  val hasReceivedAll: (List<Any>) -> Boolean = { responses: List<Any> ->
    responses.toSet() == entitiyIds
  }
  val transformResponse = { response: Responses ->
    val mappedEntities = response.getAllResponses(Entity::class).map { it.id to it }.toMap()
    callback(EntitiesResponse(mappedEntities))
  }
  val props = AwaitResponseActor.props(hasReceivedAll, transformResponse)
  val requestActor = ctx.actorOf(props)
  val requestMsg = EntityRequest(requestActor)
  entitiyIds.forEach { messageApi.send(EntityEnvelope(it, requestMsg)) }
}

data class Responses(
    private val receivedResponses: List<Any>
) {

  fun <T : Any> getResponse(responseClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.first { it::class == responseClass } as T
  }

  fun <T : Any> getAllResponses(responseClass: KClass<T>): List<T> {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses.asSequence().filterIsInstance(responseClass.java).toList()
  }

  fun getAllResponses(): List<Any> {
    @Suppress("UNCHECKED_CAST")
    return receivedResponses
  }
}

class AwaitResponseActor(
    private val action: (Responses) -> Any,
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
      LOG.debug { "Received response timeout in ${context.self().path()}. " +
          "Received so far: ${receivedResponses.map { it.javaClass.simpleName }}" }
      context.stop(self)
      return
    }

    receivedResponses.add(msg)

    try {
      if (checkResponseReceived(receivedResponses)) {
        val response = Responses(receivedResponses)
        val responseMsg = action(response)
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
        awaitedResponses: List<KClass<*>>,
        action: (Responses) -> Any
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