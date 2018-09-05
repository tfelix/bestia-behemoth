package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import net.bestia.zoneserver.battle.StatusService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class StatusComponentActor(
        private val statusService: StatusService,
        private val entityId: Long
) : AbstractActor() {

  private val tick = context.system.scheduler().schedule(
          TICK_INTERVAL,
          TICK_INTERVAL,
          self, ON_TICK_MSG, context.dispatcher(), null)

  private var manaIncrement: Float = 0.toFloat()
  private var healthIncrement: Float = 0.toFloat()

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .matchEquals(ON_TICK_MSG) { onTick() }
            .build()
  }

  private fun onTick() {

    try {

      healthIncrement += statusService.getHealthTick(entityId)
      manaIncrement += statusService.getManaTick(entityId)

      val sval = statusService.getConditionalValues(entityId)
              .orElseThrow<IllegalArgumentException>(Supplier<IllegalArgumentException> { IllegalArgumentException() })

      if (healthIncrement > 1) {

        // Update health status.
        val hpRound = healthIncrement.toInt()
        healthIncrement -= hpRound.toFloat()
        sval.addHealth(hpRound)

      }

      if (manaIncrement > 1) {

        // Update mana.
        val manaRound = manaIncrement.toInt()
        manaIncrement -= manaRound.toFloat()
        sval.addMana(manaRound)

      }

      statusService.save(entityId, sval)

    } catch (e: IllegalArgumentException) {
      // Could not tick regeneration for this entity id. Probably no
      // status component attached.
      // Terminating.
      context().stop(self)
    }
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val TICK_INTERVAL = Duration.create(StatusService.REGENERATION_TICK_RATE_MS.toLong(),
            TimeUnit.MILLISECONDS)

    private const val ON_TICK_MSG = "tickStatus"
    const val NAME = "statusComponent"
  }
}
