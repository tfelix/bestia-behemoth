package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.Cancellable
import mu.KotlinLogging
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import net.bestia.zoneserver.script.ScriptService
import java.time.Duration

private val LOG = KotlinLogging.logger { }

/**
 * This actor is used to run a periodically script function.
 *
 * @author Thomas Felix
 */
@Actor
class PeriodicScriptActor(
    private val scriptService: ScriptService,
    private val messageApi: MessageApi,
    private val entityId: Long,
    private val scriptUuid: String
) : AbstractActor() {

  private var tick: Cancellable? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(IntervalScriptCallback::class.java, this::handleDelayChange)
            .matchEquals(TICK_MSG) { onTick() }
            .build()
  }

  override fun postStop() {
    tick?.cancel()
  }

  /**
   * Sometimes if might be needed to change the delay of the script.
   *
   * @param msg
   */
  private fun handleDelayChange(msg: IntervalScriptCallback) {
    tick?.cancel()
    setupTick(msg.intervalMs)
  }

  private fun onTick() {
    awaitEntityResponse(messageApi, context, entityId) {
      try {
        scriptService.callScriptIntervalCallback(it, scriptUuid)
      } catch (e: Exception) {
        LOG.warn("Error during script interval execution. Stopping callback interval.", e)
        context().stop(self)
      }
    }
  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   */
  private fun setupTick(delayMs: Long) {
    if (delayMs < 0) {
      context.stop(self)
      return
    }

    val shed = context.system().scheduler()
    tick = shed.scheduleOnce(Duration.ofMillis(delayMs),
            self, TICK_MSG, context.dispatcher(), null)
  }

  companion object {
    private const val TICK_MSG = "onTick"
    const val NAME = "periodicScript"
  }
}
