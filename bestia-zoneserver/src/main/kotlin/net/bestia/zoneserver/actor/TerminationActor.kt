package net.bestia.zoneserver.actor

import mu.KotlinLogging
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import java.lang.Exception
import kotlin.system.exitProcess

private val LOG = KotlinLogging.logger { }

sealed class ErrorReport
object SocketBindNetworkError : ErrorReport()
class BootStepFailedError(
    val cause: Exception
) : ErrorReport()

/**
 * The [TerminationActor] actor receives system messages about e.g. failures.
 * He decides if operation can continue or if he terminates the operation
 * of the Zone.
 * Before the Zone terminates there might be cleanup jobs to perform and so on
 * thats why its important to have a single being responsible for this.
 */
@Actor
class TerminationActor : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(ErrorReport::class.java, this::handleError)
  }

  private fun handleError(msg: ErrorReport) {
    when (msg) {
      is SocketBindNetworkError -> terminateNode(msg)
    }
  }

  private fun terminateNode(errorReport: ErrorReport) {
    LOG.error { "Terminating because: $errorReport" }
    context.system.terminate()
  }

  companion object {
    const val NAME = "housekeeper"
  }
}