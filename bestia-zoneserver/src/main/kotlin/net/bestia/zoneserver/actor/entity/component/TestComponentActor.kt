package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

sealed class TestComponentMessage

data class InstallComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
) : TestComponentMessage()

data class RequestComponentMessage(
        val accountId: Long,
        val requester: ActorRef
)

data class RequestAllComponentMessage(
        val accountId: Long,
        val requester: ActorRef
)

data class ResponseComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
)

class TestComponent(
        id: Long,
        val text: String
) : net.bestia.entity.component.Component(id)

class ModifyTestComponent()


abstract class BaseComponentActor<T : net.bestia.entity.component.Component>(
        private var component: T
) : AbstractActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    createReceive(builder)

    builder.match(RequestComponentMessage::class.java, this::sendComponent)

    return builder.build()
  }

  private fun sendComponent(msg: RequestComponentMessage) {
    msg.requester.tell(ResponseComponentMessage(component), self)
  }

  protected abstract fun createReceive(builder: ReceiveBuilder)

  /**
   * Depending of the component it will check which entities of connected clients need to
   * be notified about the change.
   */
  protected fun updateEntitiesAndClients() {

  }
}

/**
 * TODOs
 * Benötige einen Service der Messages innerhalb der Aktoren korrekt weiterleitet: An die Entity oder an einen Client
 * https://medium.com/@vjames19/kotlin-futures-a-better-completablefuture-api-for-kotlin-using-extension-functions-bb50b989ea26
 *
 */
@Component
@Scope("prototype")
class MessageHubActor : AbstractActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)
  private val sendEntity = SpringExtension.actorOf(context, SendToEntityActor::class.java)

  override fun createReceive(): Receive {
    val builder = receiveBuilder()
    return builder
            .match(EntityEnvelope::class.java, this::sendToEntity)
            .match(ToClientEnvelope::class.java, this::sendToClient)
            .build()
  }

  private fun sendToClient(msg: ToClientEnvelope) {
    sendClient.tell(msg, sender)
  }

  private fun sendToEntity(msg: EntityEnvelope) {
    sendEntity.tell(msg, sender)
  }
}