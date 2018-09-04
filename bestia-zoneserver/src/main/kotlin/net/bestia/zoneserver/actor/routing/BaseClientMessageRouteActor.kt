package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.japi.pf.FI
import akka.japi.pf.ReceiveBuilder

/**
 * This class is the base class to register basic message receiving on its parent actor.
 */
abstract class BaseClientMessageRouteActor : AbstractActor() {

  /**
   * This message is send towards actors (usually an IngestActor) which will
   * then redirect all messages towards the given actor.
   */
  internal data class RedirectMessage(
          val requestedMessageClass: Class<*>,
          val receiver: ActorRef
  )

  private class Tuple<P>(
          internal val type: Class<P>,
          internal val apply: FI.UnitApply<P>
  ) {
    internal fun apply(builder: ReceiveBuilder) {
      builder.match(type, apply)
    }
  }

  private val redirections = mutableMapOf<Class<*>, MutableList<ActorRef>>()
  private val messageHandler = mutableSetOf<Tuple<*>>()

  protected fun <T> requestMessages(requestedMessageClasses: Class<T>, fn: FI.UnitApply<T>) {
    messageHandler.add(Tuple(requestedMessageClasses, fn))
  }

  @Throws(Exception::class)
  override fun preStart() {
    redirections.forEach({ (clazz: Class<*>, actors: MutableList<ActorRef>) ->
      actors.forEach { actor ->
        val msg = RedirectMessage(clazz, actor)
        context().parent().tell(msg, self)
      }
    })
  }

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    messageHandler.forEach { t -> t.apply(builder) }

    return builder.build()
  }

  private fun handleMessage(msg: Any) {
    when (msg) {
      is RedirectMessage -> handleRedirectMessage(msg)
      else -> routeMessage(msg)
    }
  }

  private fun routeMessage(msg: Any) {
    redirections[msg]?.forEach {
      it.tell(msg, sender)
    }
  }

  private fun handleRedirectMessage(msg: RedirectMessage) {
    redirections.getOrPut(msg.requestedMessageClass, { mutableListOf() }).add(msg.receiver)
  }
}