package net.bestia.zoneserver.actor.zone

import akka.actor.AbstractActor
import akka.event.Logging
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * This is a cluster singelton actor. It centralized the control over the whole
 * bestia cluster. Upon receiving control messages it performs centralized
 * orchestration like generating a new map or sending commands to server
 * instances.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class BootstrapActor : AbstractActor() {

  private val LOG = Logging.getLogger(context.system(), this)

  private var hasInitialized = false

  @Throws(Exception::class)
  override fun preStart() {
    LOG.warning("INITGLOBAL STARTED")
  }

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .matchEquals(START_MSG) { m -> this.startInit() }
            .build()
  }

  private fun startInit() {

    if (hasInitialized) {
      return
    }

    hasInitialized = true

    // Start the initialization process.
    LOG.info("Start the global server initialization...")
  }

  companion object {

    val START_MSG = "init.start"
  }
}// no op.
