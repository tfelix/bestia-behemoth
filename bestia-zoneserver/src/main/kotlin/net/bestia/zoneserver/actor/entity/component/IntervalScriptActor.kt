package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActorWithTimers
import akka.pattern.Patterns
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.entity.EntityRequest
import net.bestia.zoneserver.actor.entity.EntityResponse
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.IntervalScriptCallback
import net.bestia.zoneserver.script.ScriptService
import net.bestia.zoneserver.script.exec.ScriptCallbackExec
import java.time.Duration
import java.util.concurrent.CompletionStage

private val LOG = KotlinLogging.logger { }

/**
 * This actor is used to run a periodically script function.
 *
 * @author Thomas Felix
 */
@Actor
class IntervalScriptActor(
    private val scriptService: ScriptService,
    private val callback: IntervalScriptCallback
) : AbstractActorWithTimers() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .matchEquals(TICK_MSG) { onTick() }
        .build()
  }

  override fun preStart() {
    setupTick(callback.interval.toMillis())
  }

  // TODO How can we share this function?
  private fun getOwnerEntity(): Entity {
    val requestMsg = EntityRequest(self)
    @Suppress("UNCHECKED_CAST")
    val response = Patterns.ask(context.parent, requestMsg, Duration.ofMillis(200)) as CompletionStage<EntityResponse>

    return response.toCompletableFuture().get().entity
  }

  private fun onTick() {
    val owner = getOwnerEntity()
    val scriptCallback = ScriptCallbackExec.Builder(
        ownerEntityId = owner.id,
        uuid = callback.uuid,
        scriptCallFunction = callback.scriptKeyCallback
    ).build()

    try {
      scriptService.execute(scriptCallback)
    } catch (e: Exception) {
      LOG.warn("Error during script interval execution. Stopping callback interval.", e)
      context().stop(self)
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

    timers.startSingleTimer("script_interval", TICK_MSG, Duration.ofMillis(delayMs))
  }

  companion object {
    private const val TICK_MSG = "onTick"
    const val NAME = "periodicScript"
  }
}
