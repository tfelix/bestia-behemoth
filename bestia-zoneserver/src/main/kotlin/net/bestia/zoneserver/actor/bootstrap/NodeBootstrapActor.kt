package net.bestia.zoneserver.actor.bootstrap

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.BootStepFailedError
import net.bestia.zoneserver.actor.socket.SocketServerActor
import org.springframework.beans.factory.annotation.Qualifier
import java.time.Duration
import java.time.Instant

private val LOG = KotlinLogging.logger { }

/**
 * This actor controls the booting of the server node. Usually there is some work to be done before
 * a Zoneserver is fully operational. The NodeBoostrapActor will call all annotated
 *
 * @author Thomas Felix
 */
@Actor
class NodeBootstrapActor(
    private val bootSteps: List<NodeBootStep>,
    @Qualifier(BQualifier.SYSTEM_ROUTER)
    private val router: ActorRef
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(BootReportSuccess::class.java, this::receiveBootReport)
        .match(RegisterForBootCompleted::class.java, this::registerForBootCompleted)
        .build()
  }

  private val expectingBootReady = mutableSetOf(
      SocketServerActor::class.simpleName,
      ClusterMonitorActor::class.simpleName
  )
  private val bootreportListener = mutableSetOf<ActorRef>()

  private var bootStepsRun = false

  override fun preStart() {
    checkBootStepsStart()
  }

  private fun checkBootStepsStart() {
    if (bootStepsRun) {
      return
    }

    if (expectingBootReady.isNotEmpty()) {
      LOG.debug { "Bootreport missing from: ${expectingBootReady.joinToString(", ")}" }
      return
    }

    val start = Instant.now()
    LOG.info { "Executing ${bootSteps.size} node boot steps: ${bootSteps.map { it.bootStepName }}" }

    bootSteps.forEach { step ->
      try {
        LOG.info { "Boot Step: ${step.bootStepName}" }
        step.execute()
      } catch (e: Exception) {
        LOG.error(e) { "There was an error during a bootstep" }
        router.tell(BootStepFailedError(e), self)
        context.stop(self)
        return
      }
    }

    val bootDuration = Duration.between(start, Instant.now())
    LOG.info { "Boot steps were completed in ${bootDuration.seconds}s." }
    bootStepsRun = true

    notifyBootCompletedListener()
  }

  private fun receiveBootReport(msg: BootReportSuccess) {
    if (!expectingBootReady.contains(msg.actorClass.simpleName)) {
      LOG.warn { "Did not expect boot report from ${msg.actorClass.simpleName}" }
    }

    expectingBootReady.remove(msg.actorClass.simpleName)
    checkBootStepsStart()
  }

  private fun registerForBootCompleted(msg: RegisterForBootCompleted) {
    if (bootStepsRun) {
      LOG.debug { "Actor ${msg.actorRef} subscribed for boot completed notification but bootsteps already finished" }
      msg.actorRef.tell(BootCompleted, self)
    } else {
      LOG.debug { "Actor ${msg.actorRef} has subscribed for boot completed notification" }
      bootreportListener.add(msg.actorRef)
    }
  }

  private fun notifyBootCompletedListener() {
    bootreportListener.forEach {
      it.tell(BootCompleted, self)
    }

    LOG.debug { "Notified ${bootreportListener.size} actors with BootCompleted" }
  }

  /**
   * Message signaling that the Boot process is completed and the server
   * operational.
   */
  object BootCompleted

  data class RegisterForBootCompleted(
      val actorRef: ActorRef
  )

  data class BootReportSuccess(
      val actorClass: Class<out AbstractActor>
  )

  companion object {
    const val NAME = "nodeBootstrap"
  }
}
