package net.bestia.zoneserver

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.MessageApi

private val LOG = KotlinLogging.logger { }

class AkkaMessageApi : MessageApi {

  private var postmaster: ActorRef? = null

  override fun setPostmaster(postmaster: ActorRef) {
    this.postmaster = postmaster
  }

  override fun send(message: Any) {
    LOG.debug { "Sendig: $message" }
    postmaster!!.tell(message, ActorRef.noSender())
  }
}
