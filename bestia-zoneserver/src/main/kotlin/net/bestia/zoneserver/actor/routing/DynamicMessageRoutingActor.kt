package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

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
    val receivedMessages = mutableListOf<Class<*>>()

    /**
     * This function will request a redirect of this kind of messages from its parent.
     */
    fun <T> matchRedirect(clazz: Class<T>, fn: (T) -> Unit): BuilderFacade {
      receivedMessages.add(clazz)
      builder.match(clazz, fn)

      return this
    }

    /**
     * This function will request a redirect of this kind of messages from its parent.
     */
    fun <T> matchEqualsRedirect(obj: T, fn: (T) -> Unit): BuilderFacade {
      builder.matchEquals(obj, fn)

      return this
    }

    /**
     * This function will NOT request a redirect. The actor is only to able and process
     * this message if it gets send directly towards it. Usually you want to use
     * matchRedirect
     */
    fun <T> match(clazz: Class<T>, fn: (T) -> Unit): BuilderFacade {
      builder.match(clazz, fn)

      return this
    }
  }

  private val redirects = mutableMapOf<Class<*>, MutableList<ActorRef>>()

  private fun registerMessageRedirects(receivedMessageClasses: List<Class<*>>) {
    if (!propagateRedirectToParent) {
      return
    }

    LOG.trace { "Started: '${self.path()}' registering at parent for ${redirects.keys}" }

    receivedMessageClasses.forEach { clazz ->
      val msg = RedirectMessage(clazz, self)
      context().parent().tell(msg, self)
    }
  }

  final override fun createReceive(): Receive {
    val builder = receiveBuilder()
    val facade = BuilderFacade(builder)

    createReceive(facade)

    builder.matchAny(this::handleMessage)

    registerMessageRedirects(facade.receivedMessages)

    return builder.build()
  }

  /**
   * Override this method in children to register message receivers.
   */
  protected abstract fun createReceive(builder: BuilderFacade)

  private fun handleMessage(msg: Any) {
    when (msg) {
      is RedirectMessage -> handleRedirectMessage(msg)
      else -> routeMessage(msg)
    }
  }

  private fun routeMessage(msg: Any) {
    val registeredRedirects = redirects[msg.javaClass]

    LOG.trace { "routeMessage received: ${msg.javaClass.simpleName}, redirecting to: $registeredRedirects" }

    if (registeredRedirects == null) {
      LOG.warn { "Received message ${msg.javaClass.simpleName} but actor ${self.path()} has not target route" }
    }

    registeredRedirects?.forEach {
      it.tell(msg, sender)
    }
  }

  private fun handleRedirectMessage(msg: RedirectMessage) {
    LOG.trace { "Registered redirect for ${msg.requestedMessageClass} to '${msg.receiver}'" }
    redirects.getOrPut(msg.requestedMessageClass) { mutableListOf() }.add(msg.receiver)
  }
}