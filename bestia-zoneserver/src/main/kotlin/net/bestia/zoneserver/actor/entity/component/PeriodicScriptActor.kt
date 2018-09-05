package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.Cancellable
import mu.KotlinLogging
import net.bestia.entity.component.ScriptComponent
import net.bestia.zoneserver.script.ScriptService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

private val LOG = KotlinLogging.logger { }

/**
 * This actor is used to run a periodically script function.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class PeriodicScriptActor(
        private val scriptService: ScriptService,
        private val entityId: Long,
        private val scriptUuid: String
) : AbstractActor() {

  private var tick: Cancellable? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ScriptComponent.ScriptCallback::class.java, this::handleDelayChange)
            .matchEquals(TICK_MSG) { x -> onTick() }
            .build()
  }

  override fun postStop() {

    if (tick != null) {
      tick!!.cancel()
    }

  }

  /**
   * Sometimes if might be needed to change the delay of the script.
   *
   * @param msg
   */
  private fun handleDelayChange(msg: ScriptComponent.ScriptCallback) {
    tick!!.cancel()
    setupMoveTick(msg.intervalMs)
  }

  private fun onTick() {

    try {
      scriptService.callScriptIntervalCallback(entityId, scriptUuid)
    } catch (e: Exception) {
      LOG.warn("Error during script interval execution. Stopping callback interval.", e)
      context().stop(self)
    }

  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   */
  private fun setupMoveTick(delay: Int) {
    if (delay < 0) {
      context.stop(self)
      return
    }

    val shed = context.system().scheduler()
    tick = shed.scheduleOnce(Duration.create(delay.toLong(), TimeUnit.MILLISECONDS),
            self, TICK_MSG, context.dispatcher(), null)
  }

  companion object {
    private const val TICK_MSG = "onTick"
    const val NAME = "periodicScript"
  }
}
