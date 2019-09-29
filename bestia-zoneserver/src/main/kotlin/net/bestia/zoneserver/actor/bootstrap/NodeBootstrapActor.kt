package net.bestia.zoneserver.actor.bootstrap

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.connection.SocketServerActor
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

private val LOG = KotlinLogging.logger { }

/**
 * This actor controls the booting of the server node. Usually there is some work to be done before
 * a Zoneserver is fully operational. The NodeBoostrapActor will call all annotated
 *
 * @author Thomas Felix
 */
@Actor
class NodeBootstrapActor(
    private val bootSteps: List<NodeBootStep>
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(BootReportSuccess::class.java, this::receiveBootReport)
        .match(RegisterForBootReport::class.java, this::registerForBootReport)
        .build()
  }

  private val expectingBootReports = mutableSetOf(
      SocketServerActor::class.simpleName
  )

  private val bootreportListener = mutableMapOf<String, ActorRef>()
  private var bootStepsRun = false

  override fun preStart() {
    checkBootStepsStart()
  }

  private fun checkBootStepsStart() {
    if (!bootreportListener.keys.containsAll(expectingBootReports)) {
      LOG.debug { "Not all actors have registered for boot reporting" }
    }

    val start = Instant.now()
    LOG.info { "Booting the local node with ${bootSteps.size} steps: ${bootSteps.map { it.bootStepName }}" }

    bootSteps.forEach { step ->
      try {
        LOG.info { "Boot Step: ${step.bootStepName}" }
        step.execute()
      } catch (e: Exception) {
        LOG.error(e) { "There was an error during booting the node. Terminating." }
        exitProcess(1)
      }
    }

    val bootDuration = Duration.between(start, Instant.now())
    LOG.info { "Boot steps were completed in ${bootDuration.seconds}s." }
    bootStepsRun = true

    checkBootCompleteCondition()
  }

  private fun receiveBootReport(msg: BootReportSuccess) {
    if (!expectingBootReports.contains(msg.actorClass.simpleName)) {
      LOG.warn { "Did not expect boot report from ${msg.actorClass.simpleName}" }
    }

    expectingBootReports.remove(msg.actorClass.simpleName)
    checkBootCompleteCondition()
  }

  private fun registerForBootReport(msg: RegisterForBootReport) {
    LOG.trace { "Actor ${msg.actorClass.simpleName} has subscribed for boot report" }
    expectingBootReports.add(msg.actorClass.simpleName)
    checkBootStepsStart()
  }

  private fun checkBootCompleteCondition() {
    if (!bootStepsRun || expectingBootReports.isNotEmpty()) {
      return
    }

    LOG.debug { "All Actors reported operational status" }

    bootreportListener.values.forEach {
      it.tell(BootCompleted, self)
    }

    context.stop(self)
  }

  /**
   * Message signaling that the Boot process is completed and the server
   * operational.
   */
  object BootCompleted

  data class RegisterForBootReport(
      val actorClass: Class<out AbstractActor>
  )

  data class BootReportSuccess(
      val actorClass: Class<out AbstractActor>
  )

  companion object {
    const val NAME = "nodeBootstrapActor"
  }
}
