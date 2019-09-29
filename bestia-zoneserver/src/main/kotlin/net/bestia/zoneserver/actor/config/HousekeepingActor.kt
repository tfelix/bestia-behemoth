package net.bestia.zoneserver.actor.config

import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import kotlin.system.exitProcess

private val LOG = KotlinLogging.logger { }

sealed class ErrorReport
object SocketBindNetworkError : ErrorReport()

/**
 * The housekeeper actor receives system messages about e.g. failures.
 * He decides if operation can continue or if he terminates the operation
 * of the Zone.
 * Before the Zone terminates there might be cleanup jobs to perform and so on
 * thats why its important to have a single actor manage this.
 */
@Actor
class HousekeepingActor() : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(SocketBindNetworkError::class.java) { handleSocketBindNetworkError() }
  }

  private fun handleSocketBindNetworkError() {
    LOG.error { "Can not bind to network addr and port. Terminating." }
    // TODO Improve shutdown sequence handling here.
    exitProcess(1)
  }

  companion object {
    const val NAME = "housekeeper"
  }
}