package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder

/**
 * This class is the base class to register basic message receiving on its parent actor.
 */
abstract class DynamicMessageRoutingActor(
    private val propagateRedirectToParent: Boolean = true
) : AbstractActor() {

  /**
   * This message is send towards actors (usually an IngestActor) which will
   * then redirect all messages towards the given actor.
   */
  internal data class RedirectMessage(
      val requestedMessageClass: Class<*>,
      val receiver: ActorRef
  )

  protected class BuilderFacade(
      private val builder: ReceiveBuilder
  ) {
    private val receivedMessages = mutableListOf<Class<*>>()

    val noRedirect get() = builder

    fun <T> match(clazz: Class<T>, fn: (T) -> Unit) {
      receivedMessages.add(clazz)
      builder.match(clazz, fn)
    }

    fun <T> matchEquals(obj: T, fn: (T) -> Unit) {
      builder.matchEquals(obj, fn)
    }
  }

  private val redirects = mutableMapOf<Class<*>, MutableList<ActorRef>>()

  @Throws(Exception::class)
  override fun preStart() {
    if (!propagateRedirectToParent) {
      return
    }
    redirects.forEach { (clazz: Class<*>, actors: MutableList<ActorRef>) ->
      actors.forEach { actor ->
        val msg = RedirectMessage(clazz, actor)
        context().parent().tell(msg, self)
      }
    }
  }

  override fun createReceive(): AbstractActor.Receive {
    val builder = receiveBuilder()
    val facade = BuilderFacade(builder)
    createReceive(facade)
    builder.matchAny(this::handleMessage)

    return builder.build()
  }

  protected abstract fun createReceive(builder: BuilderFacade)

  private fun handleMessage(msg: Any) {
    when (msg) {
      is RedirectMessage -> handleRedirectMessage(msg)
      else -> routeMessage(msg)
    }
  }

  private fun routeMessage(msg: Any) {
    redirects[msg]?.forEach {
      it.tell(msg, sender)
    }
  }

  private fun handleRedirectMessage(msg: RedirectMessage) {
    redirects.getOrPut(msg.requestedMessageClass) { mutableListOf() }.add(msg.receiver)
  }
}