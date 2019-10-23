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
 * thats why its important to have a single being responsible for this.
 */
@Actor
class WatchdogActor : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(ErrorReport::class.java, this::handleError)
  }

  private fun handleError(msg: ErrorReport) {
    when(msg) {
      is SocketBindNetworkError -> {
        LOG.error { "Not being able to bind to port is fatal." }
        exitProcess(1)
      }
    }
  }

  companion object {
    const val NAME = "housekeeper"
  }
}